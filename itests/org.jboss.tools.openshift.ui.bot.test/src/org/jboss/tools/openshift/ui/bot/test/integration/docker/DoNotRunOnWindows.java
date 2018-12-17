package org.jboss.tools.openshift.ui.bot.test.integration.docker;

import org.eclipse.core.runtime.Platform;
import org.eclipse.reddeer.junit.execution.TestMethodShouldRun;
import org.junit.runners.model.FrameworkMethod;

public class DoNotRunOnWindows implements TestMethodShouldRun {

	@Override
	public boolean shouldRun(FrameworkMethod method) {
		return !(Platform.getOS().startsWith(Platform.OS_WIN32) && Platform.getOSArch().equals(Platform.ARCH_X86_64));
	}	
}