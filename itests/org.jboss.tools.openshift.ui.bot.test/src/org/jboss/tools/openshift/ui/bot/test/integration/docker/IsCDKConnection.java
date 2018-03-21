/*******************************************************************************
 * Copyright (c) 2007-2017 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v 1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributor:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.openshift.ui.bot.test.integration.docker;

import org.eclipse.reddeer.junit.execution.TestMethodShouldRun;
import org.jboss.tools.openshift.reddeer.utils.DatastoreOS3;
import org.junit.runners.model.FrameworkMethod;

/**
 * Run DeployDockerImageTest only if OpenShift connection is for CDK.
 * @author jkopriva@redhat.com
 *
 */
public class IsCDKConnection implements TestMethodShouldRun  {

	public boolean shouldRun(FrameworkMethod method) {
		return DatastoreOS3.SERVER.startsWith("https://10.0") || DatastoreOS3.SERVER.startsWith("https://192.168");
	}	
}	
