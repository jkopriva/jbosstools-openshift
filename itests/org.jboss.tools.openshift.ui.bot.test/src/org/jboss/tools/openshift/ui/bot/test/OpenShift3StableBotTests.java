/*******************************************************************************
 * Copyright (c) 2017 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v 1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributor:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.openshift.ui.bot.test;

import org.eclipse.reddeer.junit.runner.RedDeerSuite;
import org.jboss.tools.openshift.ui.bot.test.application.v3.advanced.DeleteResourceTest;
import org.jboss.tools.openshift.ui.bot.test.application.v3.advanced.InteligentDeleteResourceTest;
import org.jboss.tools.openshift.ui.bot.test.application.v3.create.CreateApplicationOnBuilderImageTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite.SuiteClasses;

/**
 * <b>OpenShift 3 Stable Tests suite</b>
 * 
 * @author jkopriva@redhat.com
 * 
 */
@RunWith(RedDeerSuite.class)
@SuiteClasses({	
//	OCBinaryLocationTest.class,
//	SetOCForNewConnectionTest.class,
//	
//	// Connection
//	OpenNewConnectionWizardTest.class,
//	CreateNewConnectionTest.class,
//	RemoveConnectionTest.class,
//	ConnectionWizardHandlingTest.class,
//	StoreConnectionTest.class,
//	ConnectionPropertiesTest.class,
//	
//	// Project
//	ProjectNameValidationTest.class,
//	LinkToCreateNewProjectTest.class,
//	CreateNewProjectTest.class,
//	DeleteProjectTest.class,
//	ResourcesTest.class,
//	ProjectPropertiesTest.class,
	DeleteResourceTest.class,
	InteligentDeleteResourceTest.class,
//	OSExplorerResourceTest.class,
//	TriggerBuildTest.class, 
//	
//	// Advanced application testing
//	DeployDockerImageTest.class,
//	DeployVariousDockerImagesTest.class,
//	EditResourceLimitsTest.class,
//	
//	// Application wizard handling
//	NewApplicationWizardHandlingTest.class,
//	TemplateParametersTest.class,
//	LabelsTest.class,
//	BuilderImageApplicationWizardHandlingTest.class,
//	
//	// Creation of a new application
//	CreateApplicationFromTemplateTest.class,
	CreateApplicationOnBuilderImageTest.class, 
//	DeploymentTest.class,
//	
//	// Application handling
//	LogsTest.class,
//	PortForwardingTest.class,
//	HandleCustomTemplateTest.class,
//	ImportApplicationWizardTest.class,
//	ImportApplicationWizardGitTest.class,
//	CreateResourcesTest.class,
//	EditResourcesTest.class,
//	ImportApplicationTest.class,
//	
//	// Server adapter
//	ServerAdapterWizardHandlingTest.class,
//	CreateServerAdapterTest.class,
//	ServerAdapterFromResourceTest.class,
//	PublishChangesTest.class,
})
public class OpenShift3StableBotTests extends AbstractBotTests {

}
