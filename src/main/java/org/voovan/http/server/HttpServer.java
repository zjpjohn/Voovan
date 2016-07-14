package org.voovan.http.server;

import org.voovan.http.monitor.Monitor;
import org.voovan.http.server.websocket.WebSocketRouter;
import org.voovan.http.server.websocket.WebSocketDispatcher;
import org.voovan.network.SSLManager;
import org.voovan.network.aio.AioServerSocket;
import org.voovan.network.messagesplitter.HttpMessageSplitter;
import org.voovan.tools.log.Logger;

import java.io.IOException;

/**
 * HttpServer 对象
 * 
 * @author helyho
 * 
 * Voovan Framework.
 * WebSite: https://github.com/helyho/Voovan
 * Licence: Apache v2 License
 */
public class HttpServer {
	private AioServerSocket		aioServerSocket;
	private HttpDispatcher	httpDispatcher;
	private WebSocketDispatcher webSocketDispatcher;
	private SessionManager sessionManager;
	private WebServerConfig config;

	/**
	 * 构造函数
	 * 
	 * @param config  WEB 配对对象
	 * @throws IOException
	 *             异常
	 */
	public HttpServer(WebServerConfig config) throws IOException {
		this.config = config;

		// 准备 socket 监听
		aioServerSocket = new AioServerSocket(config.getHost(), config.getPort(), config.getTimeout()*1000);

		//构造 SessionManage
		sessionManager = SessionManager.newInstance(config);

		//请求派发器创建
		this.httpDispatcher = new HttpDispatcher(config,sessionManager);

		this.webSocketDispatcher = new WebSocketDispatcher(config);

		//确认是否启用 HTTPS 支持
		if(config.getCertificateFile()!=null) {
			SSLManager sslManager = new SSLManager("TLS", false);
			sslManager.loadCertificate(System.getProperty("user.dir") + config.getCertificateFile(),
					config.getCertificatePassword(), config.getKeyPassword());
			aioServerSocket.setSSLManager(sslManager);
		}

		aioServerSocket.handler(new HttpServerHandler(config, httpDispatcher,webSocketDispatcher));
		aioServerSocket.filterChain().add(new HttpServerFilter());
		aioServerSocket.messageSplitter(new HttpMessageSplitter());

		//初始化并安装监控功能
		if(config.isMonitor()){
			Monitor.installMonitor(this);
		}
	}

	/**
	 * 获取配置对象
	 * @return 返回 Web 服务配置对象
     */
	public WebServerConfig getWebServerConfig() {
		return config;
	}

	/**
	 * 以下是一些 HTTP 方法的成员函数
	 */

	/**
	 * GET 请求
	 * @param routeRegexPath 匹配路径
	 * @param router  HTTP处理请求句柄
     * @return HttpServer对象
     */
	public HttpServer get(String routeRegexPath, HttpRouter router) {
		httpDispatcher.addRouteHandler("GET", routeRegexPath, router);
		return this;
	}

	/**
	 * POST 请求
	 * @param routeRegexPath 匹配路径
	 * @param router  HTTP处理请求句柄
	 * @return HttpServer对象
     */
	public HttpServer post(String routeRegexPath, HttpRouter router) {
		httpDispatcher.addRouteHandler("POST", routeRegexPath, router);
		return this;
	}

	/**
	 * HEAD 请求
	 * @param routeRegexPath 匹配路径
	 * @param router  HTTP处理请求句柄
	 * @return HttpServer对象
	 */
	public HttpServer head(String routeRegexPath, HttpRouter router) {
		httpDispatcher.addRouteHandler("HEAD", routeRegexPath, router);
		return this;
	}

	/**
	 * PUT 请求
	 * @param routeRegexPath 匹配路径
	 * @param router  HTTP处理请求句柄
	 * @return HttpServer对象
	 */
	public HttpServer put(String routeRegexPath, HttpRouter router) {
		httpDispatcher.addRouteHandler("PUT", routeRegexPath, router);
		return this;
	}

	/**
	 * DELETE 请求
	 * @param routeRegexPath 匹配路径
	 * @param router  HTTP处理请求句柄
	 * @return HttpServer对象
	 */
	public HttpServer delete(String routeRegexPath, HttpRouter router) {
		httpDispatcher.addRouteHandler("DELETE", routeRegexPath, router);
		return this;
	}

	/**
	 * TRACE 请求
	 * @param routeRegexPath 匹配路径
	 * @param router  HTTP处理请求句柄
	 * @return HttpServer对象
	 */
	public HttpServer trace(String routeRegexPath, HttpRouter router) {
		httpDispatcher.addRouteHandler("TRACE", routeRegexPath, router);
		return this;
	}

	/**
	 * CONNECT 请求
	 * @param routeRegexPath 匹配路径
	 * @param router  HTTP处理请求句柄
	 * @return HttpServer对象
	 */
	public HttpServer connect(String routeRegexPath, HttpRouter router) {
		httpDispatcher.addRouteHandler("CONNECT", routeRegexPath, router);
		return this;
	}

	/**
	 * OPTIONS 请求
	 * @param routeRegexPath 匹配路径
	 * @param router  HTTP处理请求句柄
	 * @return HttpServer对象
	 */
	public HttpServer options(String routeRegexPath, HttpRouter router) {
		httpDispatcher.addRouteHandler("OPTIONS", routeRegexPath, router);
		return this;
	}

	/**
	 * 其他请求
	 * @param method 请求方法
	 * @param routeRegexPath 匹配路径
	 * @param router  HTTP处理请求句柄
	 * @return HttpServer对象
	 */
	public HttpServer otherMethod(String method, String routeRegexPath, HttpRouter router) {
		httpDispatcher.addRouteMethod(method);
		httpDispatcher.addRouteHandler(method, routeRegexPath, router);
		return this;
	}

	/**
	 * WebSocket 服务
	 * @param routeRegexPath 匹配路径
	 * @param router WebSocket处理句柄
     */
	public void socket(String routeRegexPath, WebSocketRouter router) {
		webSocketDispatcher.addRouteHandler(routeRegexPath, router);
	}

	/**
	 * 构建新的 HttpServer,从配置文件读取配置
	 * @param config  WebServer配置类
	 * @return HttpServer 对象
	 */
	public static HttpServer newInstance(WebServerConfig config) {
		try {
			if(config!=null) {
				return new HttpServer(config);
			}else{
				Logger.error("Create HttpServer failed: WebServerConfig object is null.");
			}
		} catch (IOException e) {
			Logger.error("Create HttpServer failed.",e);
		}
		return null;
	}

	/**
	 * 构建新的 HttpServer,从配置文件读取配置
	 * @param port  HTTP 服务的端口号
	 * @return HttpServer 对象
	 */
	public static HttpServer newInstance(int port) {
		WebServerConfig config = WebContext.getWebServerConfig();
		config.setPort(port);
		return newInstance(config);
	}

	/**
	 * 构建新的 HttpServer,从配置文件读取配置
	 *
	 * @return HttpServer 对象
	 */
	public static HttpServer newInstance() {
		return newInstance(WebContext.getWebServerConfig());
	}

	/**
	 * 启动服务
	 *
	 * @return HttpServer 对象
	 */
	public HttpServer serve() {
		try {
			WebContext.welcome(config);
			aioServerSocket.start();
		} catch (IOException e) {
			Logger.error("Start HTTP server error.",e);
		}
		return this;
	}
}
