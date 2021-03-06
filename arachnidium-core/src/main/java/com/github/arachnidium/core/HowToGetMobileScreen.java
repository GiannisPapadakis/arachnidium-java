package com.github.arachnidium.core;

import java.util.List;

import com.github.arachnidium.core.fluenthandle.HowToGetHandle;
import com.github.arachnidium.core.interfaces.ICloneable;

/**
 * Strategy of a mobile context/screen receiving
 */
public class HowToGetMobileScreen extends HowToGetHandle implements ICloneable{
	
	private HowToGetPage howToGetPageStrategy;
	/**
	 * @param expected context index
	 * 
	 * @see com.github.arachnidium.core.fluenthandle.HowToGetHandle#setExpected(int)
	 */
	@Override
	public void setExpected(int index) {
		super.setExpected(index);
	}

	/**
	 * @param expected context name.
	 * A name can be defined as a regular expression
	 * 
	 * @see com.github.arachnidium.core.fluenthandle.HowToGetHandle#setExpected(java.lang.String)
	 */
	@Override
	public void setExpected(String contextRegExp) {
		super.setExpected(contextRegExp);
	}

	/**
	 * @param expected activities.
	 * Each one activity can be defined as a regular expression.
	 * 
	 * This parameter is ignored by iOS
	 * 
	 * @see com.github.arachnidium.core.fluenthandle.HowToGetHandle#setExpected(java.util.List)
	 */
	@Override
	public void setExpected(List<String> activitiesRegExps) {
		super.setExpected(activitiesRegExps);
	}
	
	@Override
	public String toString(){
		String result = "";
		if (index != null){
			result = result + " index is " + index.toString();
		}
		
		if (stringIdentifier != null){
			result = result + " context is " + stringIdentifier.toString();
		}
		
		if (uniqueIdentifiers != null){
			result = result + " activities are " + uniqueIdentifiers.toString();
		}
		
		return result;
	}	
	
	/**
	 * @see com.github.arachnidium.core.interfaces.ICloneable#cloneThis()
	 */
	@Override
	@SuppressWarnings("unchecked")
	public HowToGetMobileScreen cloneThis(){
		try {
			return (HowToGetMobileScreen) this.clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * This method is useful for WebView content recognition
	 * 
	 * @param howToGetPageStrategy is an instance of {@link HowToGetPage} where
	 * expected URLs, page indexes and titles are set up 
	 */
	public void defineHowToGetPageStrategy(HowToGetPage howToGetPageStrategy){
		this.howToGetPageStrategy = howToGetPageStrategy;
	}
	
	/**
	 * This method is useful for WebView content recognition. It is for
	 * the inner using
	 * 
	 * @return an instance of {@link HowToGetPage} where
	 * expected URLs, page indexes and titles are set up
	 */
	HowToGetPage getHowToGetPageStrategy(){
		return howToGetPageStrategy;
	}
}
