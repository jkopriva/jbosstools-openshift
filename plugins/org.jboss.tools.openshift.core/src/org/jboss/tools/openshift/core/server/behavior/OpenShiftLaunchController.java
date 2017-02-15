/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc..
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Incorporated - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.openshift.core.server.behavior;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaHotCodeReplaceListener;
import org.eclipse.jdt.launching.SocketUtil;
import org.eclipse.wst.server.core.IModule;
import org.eclipse.wst.server.core.IServer;
import org.eclipse.wst.server.core.ServerUtil;
import org.eclipse.wst.server.core.internal.Server;
import org.jboss.ide.eclipse.as.core.util.ClassCollectingHCRListener;
import org.jboss.ide.eclipse.as.wtp.core.server.behavior.AbstractSubsystemController;
import org.jboss.ide.eclipse.as.wtp.core.server.behavior.ControllableServerBehavior;
import org.jboss.ide.eclipse.as.wtp.core.server.behavior.IControllableServerBehavior;
import org.jboss.ide.eclipse.as.wtp.core.server.behavior.ILaunchServerController;
import org.jboss.ide.eclipse.as.wtp.core.server.behavior.ISubsystemController;
import org.jboss.ide.eclipse.as.wtp.core.server.launch.ServerProcess;
import org.jboss.tools.foundation.core.plugin.log.StatusFactory;
import org.jboss.tools.openshift.core.OpenShiftCoreMessages;
import org.jboss.tools.openshift.core.connection.Connection;
import org.jboss.tools.openshift.core.connection.ConnectionsRegistryUtil;
import org.jboss.tools.openshift.core.debug.DebugTrackerContributionEvaluation;
import org.jboss.tools.openshift.core.server.OpenShiftServerBehaviour;
import org.jboss.tools.openshift.core.server.OpenShiftServerUtils;
import org.jboss.tools.openshift.internal.core.OpenShiftCoreActivator;
import org.jboss.tools.openshift.internal.core.portforwarding.PortForwardingUtils;
import org.jboss.tools.openshift.internal.core.server.debug.DebuggingContext;
import org.jboss.tools.openshift.internal.core.server.debug.IDebugListener;
import org.jboss.tools.openshift.internal.core.server.debug.OpenShiftDebugUtils;
import org.jboss.tools.openshift.internal.core.util.ResourceUtils;

import com.openshift.restclient.OpenShiftException;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.capability.IBinaryCapability.OpenShiftBinaryOption;
import com.openshift.restclient.capability.resources.IPortForwardable;
import com.openshift.restclient.capability.resources.IPortForwardable.PortPair;
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IPod;
import com.openshift.restclient.model.IService;

public class OpenShiftLaunchController extends AbstractSubsystemController
		implements ISubsystemController, ILaunchServerController {

	private static final String DEBUG_MODE = "debug"; //$NON-NLS-1$
	private static final String DEV_MODE = "DEV_MODE"; //$NON-NLS-1$

	/**
	 * Get access to the ControllableServerBehavior
	 * @param configuration
	 * @return
	 * @throws CoreException
	 */
	protected static IControllableServerBehavior getServerBehavior(ILaunchConfiguration configuration) throws CoreException {
		IServer server = ServerUtil.getServer(configuration);
		IControllableServerBehavior behavior = server.getAdapter(IControllableServerBehavior.class);
		return behavior;
	}
	
	private String getLabel(String mode) {
	    String label;
	    
	    switch (mode) {
	    case ILaunchManager.DEBUG_MODE:
	        label = OpenShiftCoreMessages.DebugOnOpenshift;
	        break;
	    case ILaunchManager.PROFILE_MODE:
	        label = OpenShiftCoreMessages.ProfileOnOpenshift;
	        break;
	    default:
	        label = OpenShiftCoreMessages.RunOnOpenshift;
	        break;
	    }
	    return label;
	}

	@Override
	public void launch(ILaunchConfiguration configuration, String mode,
			ILaunch launch, IProgressMonitor monitor) throws CoreException {
		IControllableServerBehavior serverBehavior = getServerBehavior(configuration);
		if( !(serverBehavior instanceof ControllableServerBehavior )) {
			throw toCoreException("Unable to find a IControllableServerBehavior instance");
		}
		ControllableServerBehavior beh = (ControllableServerBehavior) serverBehavior;
		IServer server = beh.getServer();
        launch.addProcess(new ServerProcess(launch, server, getLabel(launch.getLaunchMode())));

		beh.setServerStarting();
		
		waitForDeploymentConfig(server, monitor);
		
		
		try {
			IDeploymentConfig dc = OpenShiftServerUtils.getDeploymentConfig(server);
			toggleDebugging(mode, monitor, beh, server, dc);
		} catch (CoreException e) {
			beh.setServerStopped();
			throw e;
		}
	}

	protected boolean waitForDeploymentConfig(IServer server, IProgressMonitor monitor) {
		boolean podsReady = podsReady(server, monitor);
		if( podsReady && !monitor.isCanceled()) {
			return deploymentConfigReady(server, monitor);
		}
		return false;
	}
	
	private boolean deploymentConfigReady(IServer server, IProgressMonitor monitor) {
		while( !monitor.isCanceled()) {
			try {
				OpenShiftServerUtils.getDeploymentConfig(server);
				return true;
			} catch(CoreException ce) {
				sleep(1000);
			}
		}
		return false;
	}
	
	private boolean podsReady(IServer server, IProgressMonitor monitor) {
		while( !monitor.isCanceled()) {
			IPod[] pods = findPods(server); // result is possibly null
			if (pods != null) {
                IPod[] buildPods = findBuildPods(pods);
                IPod[] other = findRunnablePods(pods);
                if (!complete(buildPods, other)) {
                    sleep(1000);
                } else {
                    return true;
                } 
            } else {
                return false;
            }
		}
		return false;
	}
	
	private void sleep(long t) {
		// Delay
		try {
			Thread.sleep(t);
		} catch(InterruptedException ie) {
			// Ignore
		}
	}
	
	
	private void toggleDebugging(String mode, IProgressMonitor monitor, ControllableServerBehavior beh, IServer server,
			IDeploymentConfig dc) throws CoreException {
		String currentMode = beh.getServer().getMode();
		DebuggingContext debugContext = OpenShiftDebugUtils.get().getDebuggingContext(dc);
		try {
			if (DEBUG_MODE.equals(mode)) {
				startDebugging(server, dc, debugContext, monitor);
			} else {//run, profile
				stopDebugging(dc, debugContext, monitor);
				enableDevModeForNodeJsProject(OpenShiftServerUtils.getDeploymentConfig(server), server);
			}
		} catch (CoreException e) {
			mode = currentMode;
			throw e;
		} finally {
			checkServerState(beh, currentMode, mode);
		}
	}

	/**
	 * Enables DEV_MODE environment variables in {@link IDeploymentConfig} for
	 * Node.js project by default
	 *
	 * @see <a href="https://issues.jboss.org/browse/JBIDE-22362">JBIDE-22362</a>
	 */
	private void enableDevModeForNodeJsProject(IDeploymentConfig dc, IServer server) {
		if (OpenShiftServerUtils.isNodeJsProject(server)) {

			new Job("Enabling 'DEV_MODE' for deployment config " + dc.getName()) { //$NON-NLS-1$

				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						dc.setEnvironmentVariable(DEV_MODE, Boolean.TRUE.toString());
						Connection conn = ConnectionsRegistryUtil.getConnectionFor(dc);
						conn.updateResource(dc);
					} catch (Exception e) {
						String message = "Unable to enable 'DEV_MODE' for deployment config " + dc.getName(); //$NON-NLS-1$
						OpenShiftCoreActivator.getDefault().getLogger().logError(message, e);
						return new Status(Status.ERROR, OpenShiftCoreActivator.PLUGIN_ID, message, e);
					}
					return Status.OK_STATUS;
				}

			}.schedule();
		}
	}

	private void checkServerState(ControllableServerBehavior beh, String oldMode, String mode) {
		int state = pollState();
		
		if (!Objects.equals(oldMode, mode)) {
			IModule[] modules = getServer().getModules();
			for( int i = 0; i < modules.length; i++ ) {
				((Server)getServer()).setModulePublishState(new IModule[]{modules[i]}, IServer.PUBLISH_STATE_FULL);
			}
			
			if( state == IServer.STATE_STARTED && Boolean.TRUE.equals(beh.getSharedData(OpenShiftServerBehaviour.CURRENTLY_RESTARTING))) {
				// Kick publish server job
				Job j = new Job("Publishing server " + getServer().getName()) {
					@Override
					protected IStatus run(IProgressMonitor monitor) {
						return getServer().publish(IServer.PUBLISH_INCREMENTAL, monitor);
					}
				};
				j.schedule(3000);
				beh.putSharedData(OpenShiftServerBehaviour.CURRENTLY_RESTARTING, null);
			}
		}

		
		if( state == IServer.STATE_STARTED) {
			beh.setRunMode(mode);
			beh.setServerStarted();
		} else {
			beh.setServerStopped();
			((ControllableServerBehavior)getControllableBehavior()).setRunMode(null);
		}
	}


	private void startDebugging(IServer server, IDeploymentConfig dc, DebuggingContext debugContext,
			IProgressMonitor monitor) throws CoreException {
		int remotePort = debugContext.getDebugPort();
		if( remotePort == -1 ) {
			debugContext.setDebugPort(8787);//TODO get default port from server settings?
		}
		IDebugListener listener = new IDebugListener() {
			
			@Override
			public void onDebugChange(DebuggingContext debuggingContext, IProgressMonitor monitor)
					throws CoreException {
				if (debuggingContext.getPod() == null) {
					throw toCoreException("Unable to connect to remote pod");
				}
				int localPort = mapPortForwarding(debuggingContext, monitor);
				
				if(OpenShiftServerUtils.isJavaProject(server)) {
					ILaunch debuggerLaunch = attachRemoteDebugger(server, localPort, monitor);
					if( debuggerLaunch != null ) {
						overrideHotcodeReplace(server, debuggerLaunch);
					}
				} else {
					DebugTrackerContributionEvaluation.startDebugSession(server, localPort);
				}
			}

			@Override
			public void onPodRestart(DebuggingContext debuggingContext, IProgressMonitor monitor)
					throws CoreException {
				onDebugChange(debuggingContext, monitor);
			}
		};
		debugContext.setDebugListener(listener);
		OpenShiftDebugUtils.get().enableDebugMode(dc, debugContext, monitor);
	}


	private void stopDebugging(IDeploymentConfig dc, DebuggingContext debugContext, IProgressMonitor monitor)
			throws CoreException {
		IDebugListener listener = new IDebugListener() {
			
			@Override
			public void onDebugChange(DebuggingContext debuggingContext, IProgressMonitor monitor)
					throws CoreException {
				OpenShiftDebugUtils.get().terminateRemoteDebugger(getServer());
				unMapPortForwarding(debuggingContext.getPod());
			}

			@Override
			public void onPodRestart(DebuggingContext debuggingContext, IProgressMonitor monitor)
					throws CoreException {
			}
		};
		debugContext.setDebugListener(listener);
		OpenShiftDebugUtils.get().disableDebugMode(dc, debugContext, monitor);
	}

	protected int pollState() {
		IService service = null;
		Exception e = null;
		try {
			service = OpenShiftServerUtils.getService(getServer());
		} catch(OpenShiftException ose ) {
			e = ose;
		}
		if (service == null) {
			OpenShiftCoreActivator.pluginLog().logError("The OpenShift service for server " + getServer().getName() + " could not be reached.", e);
			return IServer.STATE_STOPPED;
		}
		return IServer.STATE_STARTED;
	}
	
	@Override
	public IStatus canStart(String launchMode) {
		return Status.OK_STATUS;
	}

	@Override
	public void setupLaunchConfiguration(
			ILaunchConfigurationWorkingCopy workingCopy,
			IProgressMonitor monitor) throws CoreException {
		// Do Nothing
	}
	
	/**
	 * Unmap the port forwarding. 
	 * @throws CoreException 
	 */
	private void unMapPortForwarding(IPod pod) throws CoreException {
		if (pod != null) {
			try {
				PortForwardingUtils.stopPortForwarding(pod, null);
			} catch (IOException e) {
				throw toCoreException("Unable to stop port forawrding", e);
			}
		}
	}
	
	/**
	 * Map the remote port to a local port. 
	 * Return the local port in use, or -1 if failed
	 * @param server
	 * @param remotePort
	 * @return the local debug port or -1 if port forwarding did not start or was cancelled.
	 */
	private int mapPortForwarding(final DebuggingContext debuggingContext, final IProgressMonitor monitor) {
		
		monitor.subTask("Enabling port forwarding");
		IPod pod = debuggingContext.getPod();
		int remotePort = debuggingContext.getDebugPort();
		if (pod == null || remotePort < 1) {
			//nothing we can do
			return -1;
		}
		Set<PortPair> ports = PortForwardingUtils.getForwardablePorts(pod);
		Optional<PortPair> debugPort = ports.stream().filter(p -> remotePort == p.getRemotePort()).findFirst();
		if (!debugPort.isPresent() || monitor.isCanceled()) {
			return -1;
		}
		
		if (PortForwardingUtils.isPortForwardingStarted(pod)) {
			int p = debugPort.get().getLocalPort();
			return p;
		} 
		
		for (IPortForwardable.PortPair port : ports) {
			if(monitor.isCanceled()) {
				return -1;
			}
			port.setLocalPort(SocketUtil.findFreePort());
			monitor.worked(1);
		}
		
		PortForwardingUtils.startPortForwarding(pod, ports, OpenShiftBinaryOption.SKIP_TLS_VERIFY);
		
		if (PortForwardingUtils.isPortForwardingStarted(pod)) {
			int p = debugPort.get().getLocalPort();
			return p;
		}
		//maybe throw exception?
		return -1;
	}
	
	private static final String LAUNCH_DEBUG_PORT_PROP = "LOCAL_DEBUG_PORT";
	
	private ILaunch attachRemoteDebugger(IServer server, int localDebugPort, IProgressMonitor monitor) throws CoreException {
		monitor.subTask("Attaching remote debugger");
		ILaunch ret = null;
		OpenShiftDebugUtils debugUtils = OpenShiftDebugUtils.get();
		ILaunchConfiguration debuggerLaunchConfig = debugUtils.getRemoteDebuggerLaunchConfiguration(server);
		ILaunchConfigurationWorkingCopy workingCopy;
		if (debuggerLaunchConfig == null) {
			workingCopy = debugUtils.createRemoteDebuggerLaunchConfiguration(server);
		} else {
			if (debugUtils.isRunning(debuggerLaunchConfig, localDebugPort)) {
				return null;
			}
			workingCopy = debuggerLaunchConfig.getWorkingCopy();
		}
				
		
		IProject project = OpenShiftServerUtils.getDeployProject(server);
		debugUtils.setupRemoteDebuggerLaunchConfiguration(workingCopy, project, localDebugPort);
		debuggerLaunchConfig = workingCopy.doSave();
		
		long elapsed = 0;
		long increment = 1_000;
		long maxWait =  60_000; //TODO Get from server settings?
		boolean launched = false;
		monitor.subTask("Waiting for remote debug port availability");
		while(!launched && elapsed < maxWait) {
			try {
				//TODO That's fugly. ideally we should see if socket on debug port is responsive instead
				ret = debuggerLaunchConfig.launch(DEBUG_MODE, new NullProgressMonitor());
				ret.setAttribute(LAUNCH_DEBUG_PORT_PROP, Integer.toString(localDebugPort));
				launched = true;
			} catch (Exception e) {
				if (monitor.isCanceled()) {
					break;
				}
				try {
					Thread.sleep(increment);
					elapsed+=increment;
				} catch (InterruptedException ie) {
				}
			}
	    }
		
		if (!launched){
			throw toCoreException("Unable to start a remote debugger to localhost:"+localDebugPort);
		}
		
	    monitor.worked(10);
	    return ret;
	}

	
	private CoreException toCoreException(String msg, Exception e) {
		return new CoreException(StatusFactory.errorStatus(OpenShiftCoreActivator.PLUGIN_ID, msg, e));
	}
	
	private CoreException toCoreException(String msg) {
		return toCoreException(msg, null);
	}
	
	
	protected boolean overrideHotcodeReplace(IServer server, ILaunch launch) throws CoreException {
		IJavaHotCodeReplaceListener l = getHotCodeReplaceListener(server, launch);
		IDebugTarget[] targets = launch.getDebugTargets();
		if( targets != null && l != null) {
			for( int i = 0; i < targets.length; i++ ) {
				if( targets[i] instanceof IJavaDebugTarget) {
					((IJavaDebugTarget)targets[i]).addHotCodeReplaceListener(l);
				}
			}
		}
		return true;
	}
	protected IJavaHotCodeReplaceListener getHotCodeReplaceListener(final IServer server, final ILaunch launch) {
		return new ClassCollectingHCRListener(server, launch) {
			protected void prePublish(IJavaDebugTarget target, IModule[] modules) {
				try {
					getLaunch().terminate();
				} catch(DebugException de) {
					OpenShiftCoreActivator.pluginLog().logError(toCoreException("Unable to terminate debug session", de));
				}
			}
			
			@Override
			protected void postPublish(IJavaDebugTarget target, IModule[] modules) {
				IServer server = getServer();
				waitModulesStarted(modules);
				executeJMXGarbageCollection(server, modules);
				
				sleep(3000);
				
				String portAttr = launch.getAttribute(LAUNCH_DEBUG_PORT_PROP);
				int port = -1;
				try {
					port = Integer.parseInt(portAttr);
				} catch(NumberFormatException nfe) {
					// TODO 
				}
				try {
					ILaunch newLaunch = attachRemoteDebugger(server, port, new NullProgressMonitor());
					if( newLaunch != null ) {
						overrideHotcodeReplace(server, newLaunch);
					}
					setLaunch(newLaunch);
				} catch(CoreException ce) {
					OpenShiftCoreActivator.pluginLog().logError(toCoreException("Unable to restart debug session", ce));
				}
			}
		};
	}
	
	
	

	
	private boolean complete(IPod[] buildPods, IPod[] other) {
		if( buildPods == null || other == null )
			return false;
		if( buildPods.length != 0 && !allComplete(buildPods)) {
			return false; // wait longer
		}
		if( other.length == 0 && !allRunning(other)) {
			return false; // wait longer
		}
		
		return true;
	}

	private boolean allComplete(IPod[] pods) {
		for( int i = 0; i < pods.length; i++ ) {
			if( "Running".equalsIgnoreCase(pods[i].getStatus())) {
				return false;
			}
		}
		return true;
	}

	private boolean allRunning(IPod[] pods) {
		for( int i = 0; i < pods.length; i++ ) {
			if( !"Running".equalsIgnoreCase(pods[i].getStatus())) {
				return false;
			}
		}
		return true;
	}
	
	private IPod[] findBuildPods(IPod[] pods) {
		if( pods == null )
			return null;
		ArrayList<IPod> ret = new ArrayList<IPod>();
		for( int i = 0; i < pods.length; i++ ) {
			if( ResourceUtils.isBuildPod(pods[i]))
				ret.add(pods[i]);
		}
		return ret.toArray(new IPod[ret.size()]);
	}
	
	/*
	 * Crappy name, but just trying to find pods that aren't build pods
	 */
	private IPod[] findRunnablePods(IPod[] pods) {
		if( pods == null )
			return null;
		ArrayList<IPod> ret = new ArrayList<IPod>();
		for( int i = 0; i < pods.length; i++ ) {
			if( !ResourceUtils.isBuildPod(pods[i]))
				ret.add(pods[i]);
		}
		return ret.toArray(new IPod[ret.size()]);
	}
	
	private IPod[] findPods(IServer server) {
		Connection connection = OpenShiftServerUtils.getConnection(server);
		if (connection != null) {
			IService service = OpenShiftServerUtils.getService(server, connection);
			if (service != null) {
				List<IPod> collection = new ArrayList<IPod>();
				List<IPod> pods = connection.getResources(ResourceKind.POD, service.getProject().getName());
				List<IPod> servicePods = ResourceUtils.getPodsForService(service, pods);
				collection.addAll(pods);
				collection.addAll(servicePods);
				return collection.toArray(new IPod[collection.size()]);
			}
		}
		return null;
	}
	
}
