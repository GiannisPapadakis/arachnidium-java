/*
 +Copyright 2014 Arachnidium contributors
 +Copyright 2014 Software Freedom Conservancy
 +
 +Licensed under the Apache License, Version 2.0 (the "License");
 +you may not use this file except in compliance with the License.
 +You may obtain a copy of the License at
 +
 +     http://www.apache.org/licenses/LICENSE-2.0
 +
 +Unless required by applicable law or agreed to in writing, software
 +distributed under the License is distributed on an "AS IS" BASIS,
 +WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 +See the License for the specific language governing permissions and
 +limitations under the License.
 + */

package com.github.arachnidium.core;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.Arrays;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.internal.WrapsDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;

import com.github.arachnidium.core.bean.MainBeanConfiguration;
import com.github.arachnidium.core.components.ComponentFactory;
import com.github.arachnidium.core.components.WebdriverComponent;
import com.github.arachnidium.core.components.common.TimeOut;
import com.github.arachnidium.core.interfaces.IDestroyable;
import com.github.arachnidium.core.settings.supported.ESupportedDrivers;
import com.github.arachnidium.core.settings.supported.ExtendedCapabilityType;
import com.github.arachnidium.util.configuration.Configuration;
import com.github.arachnidium.util.configuration.interfaces.IConfigurable;
import com.github.arachnidium.util.configuration.interfaces.IConfigurationWrapper;
import com.github.arachnidium.util.logging.Log;
import com.github.arachnidium.util.reflect.executable.ExecutableUtil;

/**
 * This class creates an instance of required {@link WebDriver} implementor,
 * wraps it and creates related components ({@link WebdriverComponent})
 *
 */
public class WebDriverEncapsulation implements IDestroyable, IConfigurable,
		WrapsDriver, IConfigurationWrapper {

	private final RemoteWebDriver enclosedDriver;

	private Configuration configuration = Configuration.byDefault;
	final AbstractApplicationContext context = new AnnotationConfigApplicationContext(
			MainBeanConfiguration.class);
	private final DestroyableObjects destroyableObjects = new DestroyableObjects();
	private final TimeOut timeOut;
	private final ESupportedDrivers instantiatedESupportedDriver;
	
	/**
	 * Creates and wraps an instance of required {@link RemoteWebDriver}
	 * subclass with given {@link Capabilities}
	 * 
	 * @param supporteddriver
	 *            Is the one element from {@link ESupportedDrivers} enumeration
	 *            which contains the class of required {@link RemoteWebDriver}
	 *            subclass
	 * 
	 * @param capabilities
	 *            in an instance of {@link Capabilities}
	 */
	public WebDriverEncapsulation(ESupportedDrivers supporteddriver,
			Capabilities capabilities) {
		this(supporteddriver, returnCommonConstructorValues(supporteddriver,
				capabilities, (URL) null));
	}

	/**
	 * Creates and wraps an instance of required {@link RemoteWebDriver}
	 * subclass with given {@link Capabilities}. It should be launched on the
	 * remote host.
	 * 
	 * @param supporteddriver
	 *            Is the one element from {@link ESupportedDrivers} enumeration
	 *            which contains the class of required {@link RemoteWebDriver}
	 *            subclass
	 * 
	 * @param capabilities
	 *            in an instance of {@link Capabilities}
	 * 
	 * @param remoteAddress
	 *            is the URL of the required remote host
	 */
	public WebDriverEncapsulation(ESupportedDrivers supporteddriver,
			Capabilities capabilities, URL remoteAddress) {
		this(supporteddriver, returnCommonConstructorValues(supporteddriver,
				capabilities, remoteAddress));
	}

	private static Object[] returnCommonConstructorValues(ESupportedDrivers supporteddriver,
			Capabilities capabilities, URL remoteAddress) {
		if (supporteddriver.startsRemotely() & remoteAddress != null)
			return new Object[] { remoteAddress, capabilities };
		else {
			if (remoteAddress == null & supporteddriver.requiresRemoteURL())
				throw new RuntimeException(
						"Defined driver '"
								+ supporteddriver.toString()
								+ "' requires remote address (URL)! Please, define it in settings.json "
								+ "or use suitable constructor");
			if (remoteAddress != null)
				Log.message("Remote address " + String.valueOf(remoteAddress)
						+ " has been ignored");
			return new Object[] { capabilities };
		}
	}

	/**
	 * Allows to instantiate the selected {@link WebDriver} by given parameters.
	 * These parameters should correspond existing {@link WebDriver} constructors
	 * 
	 * @param supporteddriver the selected {@link WebDriver} representation
	 * @param values they are used to launch {@link WebDriver}
	 */
	public WebDriverEncapsulation(ESupportedDrivers supporteddriver,
			Object... values) {
		try {
			Class<? extends WebDriver> driverClass = supporteddriver.getUsingWebDriverClass();
			Constructor<?> c = ExecutableUtil.getRelevantConstructor(driverClass, values);
			
			if (c == null){
				throw new NoSuchMethodException(driverClass.getName() + " has no constructor that matches " +
						"given parameters " + Arrays.asList(values).toString());
			}
			
			enclosedDriver = (RemoteWebDriver) context.getBean(
					MainBeanConfiguration.WEBDRIVER_BEAN, context, this,
					destroyableObjects, driverClass, c.getParameterTypes(), values);
			Log.message("Getting started with " + driverClass.getSimpleName());
			timeOut = getComponent(TimeOut.class);
			resetAccordingTo(configuration);
			this.instantiatedESupportedDriver = supporteddriver;
			
			String initURL = returnInitialURL(supporteddriver, values);
			if (initURL!=null){
				enclosedDriver.get(initURL);
			}
		} catch (Exception e) {
			Log.error(
					"Attempt to create a new web driver instance has been failed! "
							+ e.getMessage(), e);
			destroy();
			throw new RuntimeException(e);
		}
	}

	private static String returnInitialURL(ESupportedDrivers supporteddriver,
			Object... values){
		
		if (!supporteddriver.isForBrowser()){
			return null;
		}
		
		String initURL = null;
		for (Object value: values){			
			if (initURL != null){
				break;
			}
			
			if (!Capabilities.class.isAssignableFrom(value.getClass())){
				continue;
			}
			
			Capabilities c = (Capabilities) value;
			initURL = (String) c.getCapability(ExtendedCapabilityType.BROWSER_INITIAL_URL);
		}
		return initURL;
	}
	
	/**
	 * Attempts to shut down {@link RemoteWebDriver} and destroys all related
	 * information
	 */
	@Override
	public void destroy() {
		if (enclosedDriver == null)
			return;
		try {
			enclosedDriver.quit();
		} catch (WebDriverException e) { // it may be already dead
			return;
		}
	}

	/**
	 * adds an object which related to {@link Webdriver} and has to be "destroyed"
	 * after quit
	 */
	public void addDestroyable(IDestroyable destroyable) {
		destroyableObjects.add(destroyable);
	}

	/**
	 * @param required {@link WebdriverComponent} subclass
	 * @return The instance of required {@link WebdriverComponent} subclass
	 */
	public <T extends WebdriverComponent> T getComponent(Class<T> required) {
		return ComponentFactory.getComponent(required, enclosedDriver);
	}

	/**
	 * 
	 * @param required {@link WebdriverComponent} subclass
	 * 
	 * @param params is a Class[] which excludes {@link WebDriver}.class
	 * {@link WebDriver} + given Class[] should match to {@link WebdriverComponent} subclass
	 * constructor parameters
	 *   
	 * @param values is a Object[] which excludes {@link WebDriver} instance
	 * {@link WebDriver} instance + given Object[] should match to {@link WebdriverComponent} subclass
	 * constructor 
	 * 
	 * @return The instance of required {@link WebdriverComponent} subclass
	 */
	public <T extends WebdriverComponent> T getComponent(Class<T> required,
			Class<?>[] params, Object[] values) {
		return ComponentFactory.getComponent(required, enclosedDriver, params,
				values);
	}

	/**
	 * @see org.openqa.selenium.internal.WrapsDriver#getWrappedDriver()
	 */
	@Override
	public WebDriver getWrappedDriver() {
		return enclosedDriver;
	}

	/**
	 * This method replaces previous {@link Configuration}
	 * and applies new given parameters 
	 * 
	 * @see com.github.arachnidium.util.configuration.interfaces.IConfigurable#resetAccordingTo(com.github.arachnidium.util.configuration.Configuration)
	 */
	@Override
	public synchronized void resetAccordingTo(Configuration config) {
		configuration = config;
		timeOut.resetAccordingTo(configuration);
	}

	/**
	 * Returns {@link Configuration}
	 * 
	 * @see com.github.arachnidium.util.configuration.interfaces.IConfigurationWrapper#getWrappedConfiguration()
	 */
	@Override
	public Configuration getWrappedConfiguration() {
		return configuration;
	}
	
	public TimeOut getTimeOut(){
		return timeOut;
	}
	
	public ESupportedDrivers getInstantiatedSupportedDriver(){
		return instantiatedESupportedDriver;
	}
}