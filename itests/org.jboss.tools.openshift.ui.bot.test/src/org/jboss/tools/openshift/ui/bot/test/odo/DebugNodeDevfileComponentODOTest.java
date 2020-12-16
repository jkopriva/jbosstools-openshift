/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.openshift.ui.bot.test.odo;

import java.io.IOException;
import java.util.Random;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.reddeer.common.exception.WaitTimeoutExpiredException;
import org.eclipse.reddeer.common.wait.TimePeriod;
import org.eclipse.reddeer.common.wait.WaitUntil;
import org.eclipse.reddeer.eclipse.ui.navigator.resources.ProjectExplorer;
import org.eclipse.reddeer.eclipse.ui.perspectives.DebugPerspective;
import org.eclipse.reddeer.junit.requirement.inject.InjectRequirement;
import org.eclipse.reddeer.junit.runner.RedDeerSuite;
import org.eclipse.reddeer.requirements.openperspective.OpenPerspectiveRequirement.OpenPerspective;
import org.eclipse.reddeer.workbench.condition.EditorWithTitleIsActive;
import org.eclipse.reddeer.workbench.impl.editor.TextEditor;
import org.jboss.tools.openshift.reddeer.requirement.CleanOpenShiftODOConnectionRequirement.CleanODOConnection;
import org.jboss.tools.openshift.reddeer.requirement.OpenShiftODOConnectionRequirement.RequiredODOConnection;
import org.jboss.tools.openshift.reddeer.requirement.OpenShiftODOProjectRequirement;
import org.jboss.tools.openshift.reddeer.requirement.OpenShiftODOProjectRequirement.RequiredODOProject;
import org.jboss.tools.openshift.reddeer.view.OpenShiftApplicationExplorerView;
import org.jboss.tools.openshift.ui.bot.test.AbstractODOTest;
import org.jboss.tools.openshift.ui.bot.test.application.v3.debug.NodeJSAppDebugTest;
import org.jboss.tools.openshift.ui.bot.test.application.v3.debug.NodeJSAppDebugTest.CursorPositionIsOnLine;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
/**
 * Create Component test for OpenShift Application Explorer
 * 
 * @author jkopriva@redhat.com
 */
@OpenPerspective(DebugPerspective.class)
@RunWith(RedDeerSuite.class)
@RequiredODOConnection
@CleanODOConnection
@RequiredODOProject
public class DebugNodeDevfileComponentODOTest extends AbstractODOTest {
  
	private static final String APP_SOURCE = "app.js";

	private static final String ECLIPSE_PROJECT = "nodeproject" + new Random().nextInt();
  
	private static final int BREAKPOINT_LINE = 34;
	

	@InjectRequirement
	private static OpenShiftODOProjectRequirement projectReq;
	
	
	@BeforeClass
	public static void setupWorkspace() {
		importLauncherProject(ECLIPSE_PROJECT, "nodejs v10-community");
		createComponent(ECLIPSE_PROJECT, projectReq.getProjectName(), "nodejs", true);
	}
	
	@Test
	@Ignore("Launcher projects do not specify debug script")
	public void checkBreakpointReached() throws CoreException, IOException {
		ProjectExplorer pe = new ProjectExplorer();
		pe.open();
		pe.getProject(ECLIPSE_PROJECT).getProjectItem(APP_SOURCE).open();
		new WaitUntil(new EditorWithTitleIsActive(APP_SOURCE));
		TextEditor editor = new TextEditor(APP_SOURCE);
		NodeJSAppDebugTest.setLineBreakpoint(editor, BREAKPOINT_LINE);
    
		OpenShiftApplicationExplorerView view = new OpenShiftApplicationExplorerView();
		view.activate();
		view.getOpenShiftODOConnection().getProject(projectReq.getProjectName()).getApplication("myapp").getComponent(ECLIPSE_PROJECT).debug();
    
		AbstractODOTest.triggerDebugSession(ECLIPSE_PROJECT, projectReq.getProjectName(), "myapp", ECLIPSE_PROJECT, "/api/greeting");

		try {
			new WaitUntil(new EditorWithTitleIsActive(APP_SOURCE), TimePeriod.LONG);
			new WaitUntil(new CursorPositionIsOnLine(editor, BREAKPOINT_LINE));
		} catch (WaitTimeoutExpiredException e) {
			Assert.fail("Debugger hasn't stopped on breakpoint");
		}
	}
}