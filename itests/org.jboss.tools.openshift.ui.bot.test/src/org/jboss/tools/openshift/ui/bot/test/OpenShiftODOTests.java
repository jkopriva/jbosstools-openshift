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
package org.jboss.tools.openshift.ui.bot.test;

import org.eclipse.reddeer.junit.runner.RedDeerSuite;
import org.jboss.tools.openshift.ui.bot.test.odo.ConnectionODOCommandsTests;
import org.jboss.tools.openshift.ui.bot.test.odo.CreateComponentODOTest;
import org.jboss.tools.openshift.ui.bot.test.odo.CreateServiceODOTest;
import org.jboss.tools.openshift.ui.bot.test.odo.LoginODOTest;
import org.jboss.tools.openshift.ui.bot.test.odo.ProjectManagementODOTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite.SuiteClasses;

/**
 * <b>OpenShift Abstract class for Tests suites</b>
 * 
 * @author jkopriva@redhat.com
 */

@RunWith(RedDeerSuite.class)
@SuiteClasses({
	LoginODOTest.class,
	ProjectManagementODOTest.class,
	ConnectionODOCommandsTests.class, 
	CreateComponentODOTest.class,
	CreateServiceODOTest.class
})
public class OpenShiftODOTests {

}
