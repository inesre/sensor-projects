package org.concord.sensor.device;

import org.concord.framework.data.stream.DataListener;
import org.concord.framework.data.stream.DataStreamDescription;
import org.concord.framework.data.stream.DataStreamEvent;
import org.concord.framework.text.UserMessageHandler;
import org.concord.sensor.SensorDevice;

import waba.sys.Vm;

public abstract class AbstractSensorDevice
	implements SensorDevice
{
	public int		startTimer =  0;
	protected Ticker ticker = null;
	protected UserMessageHandler messageHandler;
	protected 		waba.util.Vector 	dataListeners = null;
	
	protected waba.util.Vector sensorConfigs = new waba.util.Vector();
	
	public DataStreamDescription dDesc = new DataStreamDescription();
	public DataStreamEvent	processedDataEvent = new DataStreamEvent();

	protected int [] sensorChannelIndexes;
	protected float [] processedData;
	private static final int DEFAULT_BUFFERED_SAMPLE_NUM = 1000;
	private boolean prepared;
	
	int timeWithoutData = 0;
	protected String [] okOptions = {"Ok"};
	protected String [] continueOptions = {"Continue"};	
	public final static int DATA_TIME_OUT = 40;
	private boolean inDeviceRead;
	private int totalDataRead;

	public AbstractSensorDevice(Ticker t, UserMessageHandler h)
	{
		ticker = t;
		ticker.setInterfaceManager(this);
		
		messageHandler = h;
	}

	protected void tick()
	{
	    int ret;

	    // reset the total data read so we can track data coming from
	    // flushes
	    totalDataRead = 0;

	    // track when we are in the device read so if flush
	    // is called outside of this we can complain
	    inDeviceRead = true;
	    ret = deviceRead(processedData, 0, dDesc.getChannelsPerSample());
	    inDeviceRead = false;
	    
	    if(ret < 0) {
			stop();
			String message = getErrorMessage(ret);
			messageHandler.showOptionMessage(message, "Interface Error",
					continueOptions, continueOptions[0]);
			return;
	    }
	    
	    if(totalDataRead == 0) {
			// we didn't get any data.  hmm..
			timeWithoutData++;
			if(timeWithoutData > DATA_TIME_OUT){
				stop();
				messageHandler.showOptionMessage("Serial Read Error: " +
										 "possibly no interface " +
										 "connected", "Interface Error",
										 continueOptions, continueOptions[0]);					
			}
			return;
	    }
	    
	    // We either got data or there was an error
		timeWithoutData = 0;

		if(ret > 0){
			// There was some data that didn't get flushed during the read
			// so send this out to our listeners.
			processedDataEvent.setNumSamples(ret);
			notifyDataListenersReceived(processedDataEvent);				
		} 	
	}
	
	/*
	 * This is a helper method for slow devices.  It be called within deviceRead.
	 * If the data should be written into the values array passed to deviceRead
	 * the values read from the offset passed in until offset+numSamples will 
	 * be attempted to be flushed.
	 * the method returns the new offset into the data. 
	 * 
	 * You don't need to call this, but if your device is going to work on a slow
	 * computer (for example an older palm) then you will probably have to use
	 * this method.  Otherwise you will build up too much data to be processed later
	 * and then while all that data is being processed the serial buffer will overflow.
	 * 
	 * Instead this method will partially process the data.  This will give the device
	 * a better chance to "get ahead" of the serial buffer.  Once the device has gotten
	 * far enough ahead of the serial buffer it can return from deviceRead the
	 * data will be fully processed.
	 */
	protected int flushData(int numSamples)
	{
		if(!inDeviceRead) {
			// error we need an assert here but we are in waba land 
			// so no exceptions or asserts for now we'll print 
			// but later we can force a null pointer exception
			System.err.println("calling flush outside of deviceRead");
		}
		
		processedDataEvent.setNumSamples(numSamples);
		notifyDataListenersReceived(processedDataEvent);
		
		totalDataRead += numSamples;
		
		return 0;
	}
	
	protected int getBufferedSampleNum()
	{
		return DEFAULT_BUFFERED_SAMPLE_NUM;
	}
	
	protected abstract int getRightMilliseconds();
	
	protected abstract void deviceStart();
	
	protected abstract void deviceStop(boolean wasRunning);
	
	protected abstract int deviceRead(float [] values, int offset, int nextSampleOffset);

	protected abstract String getErrorMessage(int error);
	
	public void start()
	{
		deviceStart();
		
		timeWithoutData = 0;

		startTimer = Vm.getTimeStamp();
		ticker.startTicking(getRightMilliseconds());

	}
	
	/**
	 *  This doesn't really need to do anything if
	 * the sensor isn't storing any cache.
	 */
	public void reset()
	{		
	}
	
	public void stop()
	{
		boolean ticking = ticker.isTicking();

		if(ticking) {
			ticker.stopTicking();
		}
		

		deviceStop(ticking);
	}

	public DataStreamDescription getDataDescription()
	{
		return dDesc;
	}
		
	public void addDataListener(DataListener l){
		if(dataListeners == null){ dataListeners = new waba.util.Vector();	   }
		if(dataListeners.find(l) < 0){
			dataListeners.add(l);
		}
	}
	
	public void removeDataListener(DataListener l){
		if(dataListeners == null) return;
		int index = dataListeners.find(l);
		if(index >= 0) dataListeners.del(index);
		if(dataListeners.getCount() == 0) dataListeners = null;
	}

	public void notifyDataListenersEvent(DataStreamEvent e){
		if(dataListeners == null) return;
		for(int i = 0; i < dataListeners.getCount(); i++){
			DataListener l = (DataListener)dataListeners.get(i);
			l.dataStreamEvent(e);
		}
	}

	public void notifyDataListenersReceived(DataStreamEvent e)
	{
		if(dataListeners == null) return;
		for(int i = 0; i < dataListeners.getCount(); i++){
			DataListener l = (DataListener)dataListeners.get(i);
			l.dataReceived(e);
		}
	}
}