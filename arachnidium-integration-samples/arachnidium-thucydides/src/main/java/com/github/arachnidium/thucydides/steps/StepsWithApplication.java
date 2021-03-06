package com.github.arachnidium.thucydides.steps;

import com.github.arachnidium.util.configuration.Configuration;

import com.github.arachnidium.model.common.Application;

import net.thucydides.core.annotations.Step;
import net.thucydides.core.steps.ScenarioSteps;

public abstract class StepsWithApplication extends ScenarioSteps{

	private static final long serialVersionUID = 1L;
	protected Application<?, ?> app;
	
	protected void setInstantiatedApplication(Application<?, ?> app){
		this.app = app;
	}
	
	@Step
	public void quit(){
		app.quit();
	}
	
	public abstract void insatantiateApp(Configuration configuration); 

}
