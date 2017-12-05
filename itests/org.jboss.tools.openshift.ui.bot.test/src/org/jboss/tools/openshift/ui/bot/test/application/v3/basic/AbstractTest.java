package org.jboss.tools.openshift.ui.bot.test.application.v3.basic;

import org.eclipse.reddeer.workbench.handler.WorkbenchShellHandler;
import org.junit.AfterClass;

public abstract class AbstractTest {
	
	@AfterClass
	public static void cleanUpAfterTest() {
		WorkbenchShellHandler.getInstance().closeAllNonWorbenchShells();
	}
	

}
