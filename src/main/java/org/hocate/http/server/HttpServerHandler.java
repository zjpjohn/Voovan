package org.hocate.http.server;

import org.hocate.http.message.HttpRequest;
import org.hocate.http.message.HttpResponse;
import org.hocate.network.IoHandler;
import org.hocate.network.IoSession;
import org.hocate.tools.TObject;

/**
 * HttpServer 业务处理类
 * @author helyho
 *
 */
public class HttpServerHandler implements IoHandler {
	private RequestDispatch processer;
	public HttpServerHandler(RequestDispatch processer){
		this.processer = processer;
	}
	
	@Override
	public Object onConnect(IoSession session) {
		return null;
	}

	@Override
	public void onDisconnect(IoSession session) {

	}

	@Override
	public Object onReceive(IoSession session, Object obj) {
		//构造请求
		HttpRequest request = TObject.cast(obj);
		//构造响应报文并返回
		HttpResponse response = new HttpResponse();
		
		try {
			processer.Process(request, response);
		} catch (Exception e) {
			e.printStackTrace();
		}
//		System.out.println(request);
//		System.out.println("======================================");
//		try{
//			for(Part part : request.parts()){
//				System.out.println(part.getType());
//				if(part.getType()==PartType.BINARY){
//					part.saveAsFile("/Users/helyho/response.jpg");
//				}
//			}
//		}
//		catch(Exception e){
//			e.printStackTrace();
//		}
		
		
//		String bodyContent = "test body content";
//		response.body().setBody(bodyContent.getBytes());
//		System.out.println("=================================");
//		System.out.println(response);
		return response;
	}

	@Override
	public void onSent(IoSession session, Object obj) {
		// session.close();
	}

	@Override
	public void onException(IoSession session, Exception e) {
		e.printStackTrace();
	}

}