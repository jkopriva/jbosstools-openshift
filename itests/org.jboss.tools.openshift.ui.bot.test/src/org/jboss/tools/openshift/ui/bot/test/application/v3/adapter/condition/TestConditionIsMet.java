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
package org.jboss.tools.openshift.ui.bot.test.application.v3.adapter.condition;

import org.eclipse.reddeer.common.condition.WaitCondition;

/**
 * @author jkopriva@redhat.com
 * 
 */

public class TestConditionIsMet implements WaitCondition{

		private TestCondition myTest;

		public TestConditionIsMet(TestCondition myTest) {
			this.myTest = myTest;
		}

		@Override
		public boolean test() {
			return myTest.test();
		}

		@Override
		public String description() {
			return "Test condition is met";
		}

		@Override
		public <T> T getResult() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String errorMessageWhile() {
			return "Test condition is not met";
		}

		@Override
		public String errorMessageUntil() {
			return "Test condition is not met";
		}

}
