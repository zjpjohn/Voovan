package org.voovan.http.server;

import org.voovan.http.message.Request;
import org.voovan.http.message.Response;
import org.voovan.http.server.WebSocketDispatcher.WebSocketEvent;
import org.voovan.http.server.context.WebContext;
import org.voovan.http.server.context.WebServerConfig;
import org.voovan.http.websocket.WebSocketFrame;
import org.voovan.http.websocket.WebSocketFrame.Opcode;
import org.voovan.http.websocket.WebSocketTools;
import org.voovan.network.IoHandler;
import org.voovan.network.IoSession;
import org.voovan.network.messagesplitter.HttpMessageSplitter;
import org.voovan.tools.ByteBufferChannel;
import org.voovan.tools.TEnv;
import org.voovan.tools.TObject;
import org.voovan.tools.log.Logger;

import java.nio.ByteBuffer;
import java.nio.channels.InterruptedByTimeoutException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

/**
 * WebServer Socket 事件处理类
 * 
 * @author helyho
 *
 * Voovan Framework.
 * WebSite: https://github.com/helyho/Voovan
 * Licence: Apache v2 License
 */
public class WebServerHandler implements IoHandler {
	private HttpDispatcher		httpDispatcher;
	private WebSocketDispatcher	webSocketDispatcher;
	private WebServerConfig webConfig;
	private Timer keepAliveTimer;
	private List<IoSession> keepAliveSessionList;

	public WebServerHandler(WebServerConfig webConfig, HttpDispatcher httpDispatcher, WebSocketDispatcher webSocketDispatcher) {
		this.httpDispatcher = httpDispatcher;
		this.webSocketDispatcher = webSocketDispatcher;
		this.webConfig = webConfig;
		keepAliveSessionList = new Vector<IoSession>();

		keepAliveTimer = new Timer("VOOVAN_WEB@KEEPALIVE_TIMER");
		initKeepAliveTimer();
	}

	
	/**
	 * 活的基于当前时间的超时毫秒值
	 * @return
	 */
	private long getTimeoutValue(){
		int keepAliveTimeout = webConfig.getKeepAliveTimeout();
		return System.currentTimeMillis()+keepAliveTimeout*1000;
	}
	
	/**
	 * 初始化连接保持 Timer
	 */
	public void initKeepAliveTimer(){

		TimerTask keepAliveTask = new TimerTask() {
			@Override
			public void run() {

				long currentTimeValue = System.currentTimeMillis();
				//遍历所有的 session
				for(int i=0; i<keepAliveSessionList.size(); i++){

					IoSession session = keepAliveSessionList.get(i);
					long timeOutValue = (long) session.getAttribute("TimeOutValue");
					
					if(timeOutValue < currentTimeValue){
                        //如果超时则结束当前连接
                        session.close();

                        keepAliveSessionList.remove(session);
                        i--;
					}
				}
			}
		};
		keepAliveTimer.schedule(keepAliveTask, 1 , 1000);
	}

	@Override
	public Object onConnect(IoSession session) {
		return null;
	}

	@Override
	public void onDisconnect(IoSession session) {

		if ("WebSocket".equals(session.getAttribute("Type"))) {

			// 触发一个 WebSocket Close 事件
			webSocketDispatcher.fireCloseEvent(session);

			//WebSocket 要考虑释放缓冲区
			ByteBufferChannel byteBufferChannel = TObject.cast(session.getAttribute("WebSocketByteBufferChannel"));
			if (byteBufferChannel != null && !byteBufferChannel.isReleased()) {
				byteBufferChannel.release();
			}
		}

		//清理 IoSession
		keepAliveSessionList.remove(session);
	}

	@Override
	public Object onReceive(IoSession session, Object obj) {
		// 获取默认字符集
		String defaultCharacterSet = webConfig.getCharacterSet();
	
		// Http 请求
		if (obj instanceof Request) {
			// 构造请求对象
			Request request = TObject.cast(obj);
			
			// 构造响应对象
			Response response = new Response();

			if(webConfig.isGzip() && request.header().contain("Accept-Encoding") &&
					request.header().get("Accept-Encoding").contains("gzip")) {
				response.setCompress(true);
			}

			// 构造 Http 请求/响应 对象
			HttpRequest httpRequest = new HttpRequest(request, defaultCharacterSet);
			HttpResponse httpResponse = new HttpResponse(response, defaultCharacterSet);

			session.setAttribute("HttpRequest",httpRequest);
			session.setAttribute("HttpResponse",httpResponse);

			// 填充远程连接的IP 地址和端口
			httpRequest.setRemoteAddres(session.remoteAddress());
			httpRequest.setRemotePort(session.remotePort());

			// WebSocket协议升级处理
			if (WebSocketTools.isWebSocketUpgrade(request)) {
				return disposeUpgrade(session, httpRequest, httpResponse);
			}
			// Http 1.1处理
			else {
				return disposeHttp(session, httpRequest, httpResponse);
			}
		} 
		//处理 WEBSocket 报文
		else if (obj instanceof WebSocketFrame) {
			return disposeWebSocket(session, (WebSocketFrame)obj);
		}
		
		// 如果协议判断失败关闭连接
		session.close();
		return null;
	}

	/**
	 * Http 请求响应处理
	 * 
	 * @param session    HTTP-Session 对象
	 * @param httpRequest  HTTP 请求对象
	 * @param httpResponse HTTP 响应对象
	 * @return HTTP 响应对象
	 */
	public HttpResponse disposeHttp(IoSession session, HttpRequest httpRequest, HttpResponse httpResponse) {
		session.setAttribute("Type", "HTTP");

		// 处理响应请求
		httpDispatcher.process(httpRequest, httpResponse);

		//如果是长连接则填充响应报文
		if (httpRequest.header().contain("Connection")
				&& httpRequest.header().get("Connection").toLowerCase().contains("keep-alive")) {
			session.setAttribute("IsKeepAlive", true);
			httpResponse.header().put("Connection", httpRequest.header().get("Connection"));
		}

		httpResponse.header().put("Server", WebContext.getVERSION());

		return httpResponse;
	}

	/**
	 * Http协议升级处理
	 *
	 * @param session    HTTP-Session 对象
	 * @param httpRequest  HTTP 请求对象
	 * @param httpResponse HTTP 响应对象
	 * @return HTTP 响应对象
	 */
	public HttpResponse disposeUpgrade(IoSession session, HttpRequest httpRequest, HttpResponse httpResponse) {
		
		//保存必要参数
		session.setAttribute("Type", "Upgrade");

		//初始化响应消息
		httpResponse.protocol().setStatus(101);
		httpResponse.protocol().setStatusCode("Switching Protocols");
		httpResponse.header().put("Connection", "Upgrade");

		if(httpRequest.header()!=null && "websocket".equalsIgnoreCase(httpRequest.header().get("Upgrade"))){

			httpResponse.header().put("Upgrade", "websocket");
			String webSocketKey = WebSocketTools.generateSecKey(httpRequest.header().get("Sec-WebSocket-Key"));
			httpResponse.header().put("Sec-WebSocket-Accept", webSocketKey);
		}
		
		else if(httpRequest.header()!=null && "h2c".equalsIgnoreCase(httpRequest.header().get("Upgrade"))){
			httpResponse.header().put("Upgrade", "h2c");
			//这里写 HTTP2的实现,暂时留空
		}
		return httpResponse;
	}

	/**
	 * WebSocket 帧处理
	 * 
	 * @param session 	HTTP-Session 对象
	 * @param webSocketFrame WebSocket 帧对象
	 * @return WebSocket 帧对象
	 */
	public WebSocketFrame disposeWebSocket(IoSession session, WebSocketFrame webSocketFrame) {
		session.setAttribute("Type"		     , "WebSocket");
		ByteBufferChannel byteBufferChannel = null;
		if(!session.containAttribute("WebSocketByteBufferChannel")){
			byteBufferChannel = new ByteBufferChannel(session.socketContext().getBufferSize());
			session.setAttribute("WebSocketByteBufferChannel",byteBufferChannel);
		}else{
			byteBufferChannel = TObject.cast(session.getAttribute("WebSocketByteBufferChannel"));
		}

		HttpRequest reqWebSocket = TObject.cast(session.getAttribute("HttpRequest"));
		
		// WS_CLOSE 如果收到关闭帧则关闭连接
		if(webSocketFrame.getOpcode() == Opcode.CLOSING) {
			session.close();
			return WebSocketFrame.newInstance(true, Opcode.CLOSING, false, webSocketFrame.getFrameData());
		}
		// WS_PING 收到 ping 帧则返回 pong 帧
		else if(webSocketFrame.getOpcode() == Opcode.PING) {
			return WebSocketFrame.newInstance(true, Opcode.PONG, false, webSocketFrame.getFrameData());
		}
		// WS_PING 收到 pong 帧则返回 ping 帧
		else if(webSocketFrame.getOpcode() == Opcode.PONG) {
			TEnv.sleep(1000);
			return WebSocketFrame.newInstance(true, Opcode.PING, false, null);
		}else if(webSocketFrame.getOpcode() == Opcode.CONTINUOUS){
			byteBufferChannel.writeEnd(webSocketFrame.getFrameData());
		}
		// WS_RECIVE 文本和二进制消息出发 Recived 事件
		else if (webSocketFrame.getOpcode() == Opcode.TEXT || webSocketFrame.getOpcode() == Opcode.BINARY) {

			byteBufferChannel.writeEnd(webSocketFrame.getFrameData());
			WebSocketFrame respWebSocketFrame = null;
			
			//判断解包是否有错
			if(webSocketFrame.getErrorCode()==0){
				respWebSocketFrame = webSocketDispatcher.process(WebSocketEvent.RECIVED, session, reqWebSocket, byteBufferChannel.getByteBuffer());
				byteBufferChannel.compact();
				byteBufferChannel.clear();
			}else{
				//解析时出现异常,返回关闭消息
				respWebSocketFrame = WebSocketFrame.newInstance(true, Opcode.CLOSING, false, ByteBuffer.wrap(WebSocketTools.intToByteArray(webSocketFrame.getErrorCode(), 2)));
			}
			return respWebSocketFrame;
		}

		return null;
	}

	@Override
	public void onSent(IoSession session, Object obj) {
		HttpRequest request = TObject.cast(session.getAttribute("HttpRequest"));
		HttpResponse response = TObject.cast(session.getAttribute("HttpResponse"));

		//WebSocket 协议处理
		if(HttpMessageSplitter.isWebSocketFrame((ByteBuffer) obj) != -1){
			WebSocketFrame webSocketFrame = WebSocketFrame.parse((ByteBuffer)obj);
			if(webSocketFrame.getOpcode() != Opcode.PING &&
					webSocketFrame.getOpcode() != Opcode.PONG &&
					webSocketFrame.getOpcode() != Opcode.CLOSING) {

				webSocketDispatcher.process(WebSocketEvent.SENT, session, request, webSocketFrame.getFrameData());
			}
		}

		//针对 WebSocket 的处理协议升级
		if("Upgrade".equals(session.getAttribute("Type"))){
			session.setAttribute("Type", "WebSocket");

			//触发 onOpen 事件
			WebSocketFrame webSocketFrame = webSocketDispatcher.process(WebSocketEvent.OPEN, session, request, null);

			if(webSocketFrame!=null) {

				//发送 onOpen 方法的数据
				ByteBuffer byteBuffer = webSocketFrame.toByteBuffer();

				//这里不用syncSend 方法是因为出发 onSent 是异步的,会导致消息顺序错乱
				session.send(byteBuffer);
				byteBuffer.rewind();

				//出发 onSent 事件
				onSent(session, byteBuffer);
			}

			//发送 ping 消息
			WebSocketFrame ping = WebSocketFrame.newInstance(true, Opcode.PING, false, null);
			session.send(ping.toByteBuffer());
		}

		//WebSocket 不做 KeepAlive 的控制
		if( !"WebSocket".equals(session.getAttribute("Type")) ) {
			//处理连接保持
			if (((Boolean) true).equals(session.getAttribute("IsKeepAlive")) &&
					webConfig.getKeepAliveTimeout() > 0) {

				if (!keepAliveSessionList.contains(session)) {
					keepAliveSessionList.add(session);
				}
				//更新会话超时时间
				session.setAttribute("TimeOutValue", getTimeoutValue());
			} else {
				if (keepAliveSessionList.contains(session)) {
					keepAliveSessionList.remove(session);
				}
				session.close();
			}
		}
	}

	@Override
	public void onException(IoSession session, Exception e) {
		//忽略远程连接断开异常 和 超时断开异常
		if(!(e instanceof InterruptedByTimeoutException)){
			Logger.error("Http Server Error",e);
		}
		session.close();
	}
}
