package org.hocate.http.server;

import java.io.IOException;

import org.hocate.network.aio.AioServerSocket;
import org.hocate.network.messagePartition.HttpMessageParter;

/**
 * HttpServer 对象
 * @author helyho
 * 
 */
public class HttpServer {
	private AioServerSocket aioServerSocket;
	private RequestDispatch requestProcesser;
	
	/**
	 * 构造函数
	 * @param host			监听地址
	 * @param port			监听端口
	 * @param timeout		超时时间
	 * @param rootDir		根目录
	 * @throws IOException	异常
	 */
	public HttpServer(String host,int port,int timeout,String rootDir) throws IOException{
		//路由处理对象
		requestProcesser = new RequestDispatch(rootDir);
		
		//准备 socket 监听
		aioServerSocket = new AioServerSocket(host,port,timeout);
		aioServerSocket.handler(new HttpServerHandler(requestProcesser));
		aioServerSocket.filterChain().add(new HttpServerFilter());
		aioServerSocket.messageParter(new HttpMessageParter());
	}
	
	/**
	 * 以下是一些 HTTP 方法的成员函数
	 */
	
	public void get(String routeRegexPath,RouterBuiz routeBuiz){
		requestProcesser.addRouteRuler("GET", routeRegexPath, routeBuiz);
	}
	
	public void post(String routeRegexPath,RouterBuiz routeBuiz){
		requestProcesser.addRouteRuler("POST", routeRegexPath, routeBuiz);
	}
	
	public void head(String routeRegexPath,RouterBuiz routeBuiz){
		requestProcesser.addRouteRuler("HEAD", routeRegexPath, routeBuiz);
	}
	
	public void put(String routeRegexPath,RouterBuiz routeBuiz){
		requestProcesser.addRouteRuler("PUT", routeRegexPath, routeBuiz);
	}
	
	public void delete(String routeRegexPath,RouterBuiz routeBuiz){
		requestProcesser.addRouteRuler("delete", routeRegexPath, routeBuiz);
	}
	
	public void trace(String routeRegexPath,RouterBuiz routeBuiz){
		requestProcesser.addRouteRuler("TRACE", routeRegexPath, routeBuiz);
	}
	
	public void connect(String routeRegexPath,RouterBuiz routeBuiz){
		requestProcesser.addRouteRuler("CONNECT", routeRegexPath, routeBuiz);
	}
	
	public void options(String routeRegexPath,RouterBuiz routeBuiz){
		requestProcesser.addRouteRuler("OPTIONS", routeRegexPath, routeBuiz);
	}
	
	public void otherMethod(String method,String routeRegexPath,RouterBuiz routeBuiz){
		requestProcesser.addRouteMethod(method);
		requestProcesser.addRouteRuler(method, routeRegexPath, routeBuiz);
	}
	
	/**
	 * 启动服务
	 * @throws IOException
	 */
	public void Serve() throws IOException{
		aioServerSocket.start();
	}
}