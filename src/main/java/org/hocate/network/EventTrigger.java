package org.hocate.network;

import java.nio.ByteBuffer;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.hocate.network.Event.EventName;
import org.hocate.network.Event.EventState;

/**
 * 事件触发器
 * 		出发各种事件
 * @author helyho
 *
 */
public class EventTrigger {
	
	private IoSession session;
	private ThreadPoolExecutor eventThreadPool;
	private Vector<Event> eventPool;
	
	/**
	 * 构造函数
	 * @param session
	 */
	public EventTrigger(IoSession session){
		this.session = session;
		eventThreadPool =ThreadPool.getThreadPool();
		eventPool = new Vector<Event>();
	}
	
	/**
	 * 无参数构造函数
	 */
	public EventTrigger(){
		int cpuCoreCount = Runtime.getRuntime().availableProcessors();
		eventThreadPool = new ThreadPoolExecutor(cpuCoreCount, cpuCoreCount+2,20, TimeUnit.SECONDS,new LinkedBlockingQueue<Runnable>());
		//设置allowCoreThreadTimeOut,允许回收超时的线程
		eventThreadPool.allowCoreThreadTimeOut(true);
	}
	
	public Vector<Event> getEventPool() {
		return eventPool;
	}

	public void fireAcceptThread(IoSession session){
		clearFinishedEvent();
		fireEventThread(session,EventName.ON_ACCEPTED,null);
	}
	
	public void fireConnectThread(){
		clearFinishedEvent();
		fireEventThread(EventName.ON_CONNECT,null);
	}
	
	public void fireReceiveThread(){
		clearFinishedEvent();
		//当消息长度大于缓冲区时,receive 会在缓冲区满了后就出发,这时消息还没有发送完,会被触发多次
		//所以当有 receive 事件正在执行则抛弃后面的所有 receive 事件
		//!hasEventDisposeing(EventName.ON_CONNECT) &&
		if((session.getSSLParser()==null || session.getSSLParser().isHandShakeDone())
			&& !hasEventDisposeing(EventName.ON_RECEIVE) 
				&& session.isConnect())
		{
			fireEventThread(EventName.ON_RECEIVE,null);
		}
	}
	
	public void fireSentThread(ByteBuffer buffer){
		clearFinishedEvent();
		fireEventThread(EventName.ON_SENT,buffer);
	}
	
	public void fireDisconnectThread(){
		clearFinishedEvent();
		fireEventThread(EventName.ON_DISCONNECT,null);
	}
	
	public void fireExceptionThread(Exception exception){
		clearFinishedEvent();
		fireEventThread(EventName.ON_EXCEPTION,exception);
	}
	
	public void fireAccept(IoSession session){
		clearFinishedEvent();
		fireEvent(session,EventName.ON_ACCEPTED,null);
	}

	public void fireConnect(){
		clearFinishedEvent();
		fireEvent(EventName.ON_CONNECT,null);
	}
	
	public void fireReceive(){
		clearFinishedEvent();
		//当消息长度大于缓冲区时,receive 会在缓冲区满了后就出发,这时消息还没有发送完,会被触发多次
		//所以当有 receive 事件正在执行则抛弃后面的所有 receive 事件
		//!hasEventDisposeing(EventName.ON_CONNECT) &&
		if((session.getSSLParser()==null || session.getSSLParser().isHandShakeDone())
				&& !hasEventDisposeing(EventName.ON_RECEIVE) 
				&& session.isConnect())
		{
			fireEvent(EventName.ON_RECEIVE,null);
		}
	}
	
	public void fireSent(ByteBuffer buffer){
		clearFinishedEvent();
		fireEvent(EventName.ON_SENT,buffer);
	}
	
	public void fireDisconnect(){
		clearFinishedEvent();
		fireEvent(EventName.ON_DISCONNECT,null);
	}
	
	public void fireException(Exception exception){
		clearFinishedEvent();
		fireEvent(EventName.ON_EXCEPTION,exception);
	}
	
	
	public boolean isShutdown(){
		clearFinishedEvent();
		return eventThreadPool.isShutdown();
	}
	
	public void shutdown(){
		eventThreadPool.shutdown();
	}
	
	public IoSession getSession(){
		return session;
	}
	
	/**
	 * 判断有没有特定的事件在执行
	 * @param eventName  事件名
	 * @return
	 */
	public boolean hasEventDisposeing(EventName eventName){
		for(Event event : eventPool){
			if(event.getName() == eventName && event.getState() == EventState.DISPOSEING){
				return true;
			}
		}
		return false;
	}
	
	public void clearFinishedEvent(){
		for(int i=0;i<eventPool.size();i++){
			Event event = eventPool.get(i);
			if(event.getState() == EventState.FINISHED){
				eventPool.remove(event);
				i--;
			}
		}
	}
	
	/**
	 * 事件触发
	 * 		根据事件启动 EventThread 来处理事件
	 * @param session  当前连接会话
	 * @param name     事件名称
	 * @param exception
	 */
	public void fireEventThread(IoSession session,Event.EventName name,Object other){
		if(!eventThreadPool.isShutdown()){
			Event event = Event.getInstance(session,name,other);
			eventPool.add(event);
			eventThreadPool.execute(new EventThread(event));
		}
	}
	
	/**
	 * 事件触发
	 * 		根据事件启动 EventThread 来处理事件
	 * @param name     事件名称
	 * @param exception
	 */
	public void fireEventThread(Event.EventName name,Object other){
		fireEventThread(session, name,other);
	}
	
	
	/**
	 * 事件触发
	 * 		根据事件启动 EventThread 来处理事件
	 * @param session  当前连接会话
	 * @param name     事件名称
	 * @param exception
	 */
	public void fireEvent(IoSession session,Event.EventName name,Object other){
		Event event = Event.getInstance(session,name,other);
		EventProcess.process(event);
		eventPool.add(event);
	}
	
	/**
	 * 事件触发
	 * 		根据事件启动 EventThread 来处理事件
	 * @param name     事件名称
	 * @param exception
	 */
	public void fireEvent(Event.EventName name,Object other){
		fireEventThread(session, name,other);
	}

}