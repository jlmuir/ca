package org.epics.ca.impl;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.epics.ca.AccessRights;
import org.epics.ca.Channel;
import org.epics.ca.Listener;
import org.epics.ca.Monitor;
import org.epics.ca.Status;
import org.epics.ca.data.Metadata;

import com.lmax.disruptor.dsl.Disruptor;

public class ChannelImpl<T> implements Channel<T>
{
	protected final String name;
	protected final Class<T> channelType;
	
	protected T value;
	
	public ChannelImpl(String name, Class<T> channelType)
	{
		this.name = name;
		this.channelType = channelType;
		
		/*
		// TODO
		// map channelType to base DBR type
		// meta DBR type = meta base + channel base
		TypeSupport ts = Util.getTypeSupport(channelType);
		System.out.println(ts.getClass().getName() + " code " + ts.getCode());
		*/
	}
	
	@Override
	public void close() {
		// TODO Auto-generated method stub
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public CompletableFuture<Channel<T>> connect() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Listener addConnectionListener(
			BiConsumer<Channel<T>, Boolean> handler) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Listener addAccessRightListener(
			BiConsumer<Channel<T>, AccessRights> handler) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public T get() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void put(T value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public CompletableFuture<T> getAsync() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<Status> putAsync(T value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <MT extends Metadata<T>> MT get(@SuppressWarnings("rawtypes") Class<? extends Metadata> clazz) {
		// TODO Auto-generated method stub
		return null;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public <MT extends Metadata<T>> CompletableFuture<MT> getAsync(
			Class<? extends Metadata> clazz) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Monitor<T> addValueMonitor(Consumer<? extends T> handler, int queueSize) {
		// TODO Auto-generated method stub
		return null;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public <MT extends Metadata<T>> Monitor<MT> addMonitor(
			Class<? extends Metadata> clazz, Consumer<? extends Metadata> handler, int queueSize) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Monitor<T> addValueMonitor(Disruptor<T> disruptor) {
		// TODO Auto-generated method stub
		return null;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public <MT extends Metadata<T>> Monitor<MT> addMonitor(
			Class<? extends Metadata> clazz, Disruptor<? extends Metadata> disruptor) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public Map<String, Object> getProperties() {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	public <VT extends Metadata<T>> Monitor<VT> createMonitor(@SuppressWarnings("rawtypes") Class<? extends Metadata> clazz)
	{
        // Executor that will be used to construct new threads for consumers
        Executor executor = Executors.newCachedThreadPool();

        // Specify the size of the ring buffer, must be power of 2.
        int bufferSize = 4;
        
        // Event factory.... get out of TypeSupport
        @SuppressWarnings("unchecked")
		EventFactory<VT> eventFactory = (EventFactory<VT>)Util.getEventFactory(clazz);
        
        //Disruptor<VT> disruptor = 
         new Disruptor<>(eventFactory, bufferSize, executor,
	    		ProducerType.SINGLE, new SleepingWaitStrategy(10));
        
        return null;
	}

	*/
	
}