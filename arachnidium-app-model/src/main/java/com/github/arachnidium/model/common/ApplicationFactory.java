package com.github.arachnidium.model.common;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.Arrays;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;

import com.github.arachnidium.core.Handle;
import com.github.arachnidium.core.Manager;
import com.github.arachnidium.core.WebDriverEncapsulation;
import com.github.arachnidium.core.settings.CapabilitySettings;
import com.github.arachnidium.core.settings.WebDriverSettings;
import com.github.arachnidium.core.settings.supported.ESupportedDrivers;
import com.github.arachnidium.util.configuration.Configuration;
import com.github.arachnidium.util.proxy.EnhancedProxyFactory;
import com.github.arachnidium.util.reflect.executable.ExecutableUtil;

/**
 * Utility class that contains methods which create {@link Application}
 * instances
 */
public abstract class ApplicationFactory {
	protected Configuration config; //By this configuration app will be launched
	protected ESupportedDrivers supportedDriver; //desired WebDriver
	protected Capabilities capabilities;//desired capabilities
	protected URL remoteUrl; //URL to the desired remote host 
	
	/**
	 * If factory instantiated this way 
	 * the app will be started using the given {@link Configuration}
	 */
	protected ApplicationFactory(Configuration configuration){
		super();
		config = configuration;
		WebDriverSettings wdSettings = configuration.getSection(WebDriverSettings.class);
		supportedDriver = configuration.getSection(WebDriverSettings.class).getSupoortedWebDriver();
		Capabilities caps = configuration.getSection(CapabilitySettings.class);
		remoteUrl = wdSettings.getRemoteAddress();
		
		if (caps == null){
			capabilities = supportedDriver.getDefaultCapabilities();
			return;
		}

		if (caps.asMap().size() == 0){
			capabilities = supportedDriver.getDefaultCapabilities();
			return;
		}

		DesiredCapabilities dc = new DesiredCapabilities();
		capabilities = dc.merge(supportedDriver.getDefaultCapabilities()).merge(
					caps);		
	}
	
	/**
	 * If factory instantiated this way 
	 * the app will be started using {@link Configuration#byDefault}
	 */
	protected ApplicationFactory(){
		this(Configuration.byDefault);
	}
	
	/**
	 * If factory instantiated this way 
	 * the app will be started using desired
	 * {@link WebDriver} description
	 * and its default {@link Capabilities} 
	 */
	protected ApplicationFactory(ESupportedDrivers supportedDriver){
		this(supportedDriver, supportedDriver.getDefaultCapabilities(), null);
	}
	
	/**
	 * If factory instantiated this way 
	 * the app will be started using desired
	 * {@link WebDriver} description
	 * and given {@link Capabilities} 
	 */
	protected ApplicationFactory(ESupportedDrivers supportedDriver, 
			Capabilities capabilities){
		this(supportedDriver, capabilities, null);
	}	
	
	/**
	 * If factory instantiated this way 
	 * the app will be started using desired
	 * {@link WebDriver} description, given {@link Capabilities} 
	 * and URL to the desired remote host
	 */
	protected ApplicationFactory(ESupportedDrivers supportedDriver, 
			Capabilities capabilities, URL remoteUrl){
		this.supportedDriver = supportedDriver;
		config = null;
		this.capabilities = capabilities;
		this.remoteUrl = remoteUrl;
	}	
	
	protected interface WebDriverDesignationChecker {
		void checkGivenDriver(ESupportedDrivers givenWebDriverDesignation)
				throws IllegalArgumentException;
	}
	
	private Class<?>[] getInitParamClasses(){
		if (remoteUrl != null){
			return new Class[]{ESupportedDrivers.class, Capabilities.class, URL.class};
		}
		
		return new Class[]{ESupportedDrivers.class, Capabilities.class};
	}
	
	private Object[] getInitParamValues(){
		if (remoteUrl != null){
			return new Object[]{supportedDriver, capabilities, remoteUrl};
		}
		
		return new Object[]{supportedDriver, capabilities};
	}	

	protected <T extends Application<?, ?>> T launch(
			Class<? extends Manager<?,?>> handleManagerClass, Class<T> appClass,
			WebDriverDesignationChecker objectWhichChecksWebDriver) {
		Handle h = null;
		try {
			objectWhichChecksWebDriver.checkGivenDriver(supportedDriver);
			prelaunch();
			h = getTheFirstHandle(handleManagerClass, getInitParamClasses(),
					getInitParamValues());
			if (config != null){
				h.driverEncapsulation.resetAccordingTo(config);
			}
			
			Object[] params = new Object[] { h };
			Constructor<?> c = ExecutableUtil.getRelevantConstructor(appClass, params);
			
			if (c == null){
				throw new RuntimeException(new NoSuchMethodException("There is no cunstructor which matches to " + Arrays.asList(params).toString() + 
						". The target class is " + appClass.getName()));
			}
			
			T result = EnhancedProxyFactory.getProxy(appClass,
					c.getParameterTypes(),
					new Object[] { h }, new ApplicationInterceptor() {
					});
			DecompositionUtil.populateFieldsWhichAreDecomposable(result);
			return result;
		} catch (Exception e) {
			if (h != null) {
				h.driverEncapsulation.destroy();
			}
			throw new RuntimeException(e);
		}	
	}	
	
	/**
	 * The starting of the desired application by given parameters
	 * 
	 * @param appClass is the desired app representation
	 * @return an instance of the given appClass
	 */
	public abstract <T extends Application<?, ?>> T launch(Class<T> appClass);

	private void prelaunch() {
		supportedDriver.launchRemoteServerLocallyIfWasDefined();
		if (config == null){
			supportedDriver.setSystemProperty(Configuration.byDefault, capabilities);
			return;
		}
		supportedDriver.setSystemProperty(config, capabilities);
	}
	
	static Handle getTheFirstHandle(
			Class<? extends Manager<?,?>> handleManagerClass,
			Class<?>[] wdEncapsulationParams, Object[] wdEncapsulationParamVals) {
		try {
			Constructor<?> wdeC = WebDriverEncapsulation.class
					.getConstructor(wdEncapsulationParams);
			WebDriverEncapsulation wdeInstance = (WebDriverEncapsulation) wdeC
					.newInstance(wdEncapsulationParamVals);
			
			Constructor<?> c = handleManagerClass
					.getConstructor(new Class<?>[] { WebDriverEncapsulation.class });
			Manager<?,?> m = (Manager<?,?>) c
					.newInstance(new Object[] { wdeInstance });
	
			return m.getHandle(0);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
