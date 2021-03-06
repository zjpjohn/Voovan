package org.voovan.tools.log;

import org.voovan.tools.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 *	格式化日志信息并输出
 *
 *使用包括特殊的定义{{}}
 *{{s}}:  一个空格
 *{{t}}:  制表符
 *{{n}}:  换行
 *================================
 *{{I}}:  消息内容,即要展示的日志内容
 *{{F}}:  源码文件名
 *{{SI}}: 栈信息输出
 *{{L}}:  当前代码的行号
 *{{M}}:  当前代码的方法名
 *{{C}}:  当前代码的类名称
 *{{T}}:  当前线程名
 *{{D}}:  当前系统时间
 *{{R}}:  从启动到当前代码执行的事件
 * @author helyho
 * 
 * Voovan Framework.
 * WebSite: https://github.com/helyho/Voovan
 * Licence: Apache v2 License
 */
public class Formater {
	private String template;
	private LoggerThread loggerThread;
	private List<String> logLevel;
	private String dateStamp; 

	/**
	 * 构造函数
	 * @param template 模板
	 */
	public Formater(String template) {
		this.template = template;
		logLevel = new Vector<String>();
		for(String level : StaticParam.getLogConfig("LogLevel",StaticParam.LOG_LEVEL).split(",")){
			logLevel.add(level.trim());
		}
		dateStamp = TDateTime.now("YYYYMMdd");
	}

	/**
	 * 获取日志记录级别信息
	 * @return 获取日志记录级别信息
	 */
	public List<String> getLogLevel() {
		return logLevel;
	}

	/**
	 * 获得当前栈元素信息
	 * @return 栈信息元素
	 */
	public static StackTraceElement currentStackLine() {
		StackTraceElement[] stackTraceElements = TEnv.getStackElements();
		return stackTraceElements[6];
	}

	/**
	 * 获取当前线程名称
	 * @return 当前线程名
	 */
	private static String currentThreadName() {
		Thread currentThread = Thread.currentThread();
		return currentThread.getName()+" : "+currentThread.getId();
	}

	/**
	 * 消息缩进
	 * @param message 消息对象
	 * @return 随进后的消息
	 */
	private String preIndentMessage(Message message){
		String infoIndent = StaticParam.getLogConfig("InfoIndent",StaticParam.LOG_INFO_INDENT);
		String msg = message.getMessage();
		if(infoIndent!=null && !infoIndent.isEmpty()){
			msg = infoIndent + msg;
			msg = msg.replaceAll("\n", "\n" + infoIndent);
			message.setMessage(msg);
		}
		return msg;
	}

	/**
	 * 构造消息格式化 Token
	 * @param message 消息对象
	 * @return  token 集合
     */
	public Map<String, String> newLogtokens(Message message){
		Map<String, String> tokens = new HashMap<String, String>();
		StackTraceElement stackTraceElement = currentStackLine();
		
		//Message和栈信息公用
		tokens.put("t", "\t");
		tokens.put("s", " ");
		tokens.put("n", "\r\n");
		tokens.put("I", preIndentMessage(message)); //日志消息
		
		//栈信息独享
		
		tokens.put("P", TObject.nullDefault(message.getLevel(),"INFO"));			//信息级别
		tokens.put("SI", stackTraceElement.toString());									//堆栈信息
		tokens.put("L", Integer.toString((stackTraceElement.getLineNumber())));			//行号
		tokens.put("M", stackTraceElement.getMethodName());								//方法名
		tokens.put("F", stackTraceElement.getFileName());								//源文件名
		tokens.put("C", stackTraceElement.getClassName());								//类名
		tokens.put("T", currentThreadName());											//线程
		tokens.put("D", TDateTime.now("YYYY-MM-dd HH:mm:ss:SS z"));						//当前时间 
		tokens.put("R", Long.toString(System.currentTimeMillis() - StaticParam.getStartTimeMillis())); //系统运行时间
		
		return tokens;
	}
	
	/**
	 * 格式化消息
	 * @param message 消息对象
	 * @return 格式化后的消息
	 */
	public String format(Message message) {
		Map<String, String> tokens = newLogtokens(message);
		return TString.tokenReplace(template, tokens);
	}

	/**
	 * 简单格式化
	 * @param message 消息对象
	 * @return 格式化后的消息
     */
	public String simpleFormat(Message message){
		//消息缩进
		Map<String, String> tokens = newLogtokens(message);
		preIndentMessage(message);
		return TString.tokenReplace(message.getMessage(), tokens);
	}
	
	/**
	 * 消息类型是否可以记录
	 * @param message 消息对象
	 * @return 是否可写入
	 */
	public boolean messageWritable(Message message){
		if(logLevel.contains("ALL")){
			return true;
		}
		else if(logLevel.contains(message.getLevel())){
			return true;
		}else{
			return false;
		}
	}
	
	/**
	 * 写入消息对象,在进行格式化后的写入
	 * @param message 消息对象
	 */
	public void writeFormatedLog(Message message) {
		if(messageWritable(message)){
			if("SIMPLE".equals(message.getLevel())){
				writeLog(simpleFormat(message)+"\r\n");
			}else{
				writeLog(format(message));
			}
		}
	}
	
	/**
	 * 写入消息
	 * @param msg 消息字符串
	 */
	public synchronized void writeLog(String msg) {
		if(Logger.isState()){
			if (loggerThread == null || loggerThread.isFinished()) {
				this.loggerThread = LoggerThread.start(getOutputStreams());
			}
			//如果日志发生变化则产生新的文件
			if(!dateStamp.equals(TDateTime.now("YYYYMMdd"))){
				loggerThread.setOutputStreams(getOutputStreams());
			}
			
			loggerThread.addLogMessage(msg);
		}
	}
	
	/**
	 * 获取格式化后的日志文件路径
	 * @return 返回日志文件名
	 */
	public static String getFormatedLogFilePath(){
		String filePath = "";
		String logFile = StaticParam.getLogConfig("LogFile",StaticParam.LOG_FILE);
		if(logFile!=null) {
			Map<String, String> tokens = new HashMap<String, String>();
			tokens.put("D", TDateTime.now("YYYYMMdd"));
			tokens.put("WorkDir", TFile.getContextPath());
			filePath = TString.tokenReplace(logFile, tokens);
			String fileDirectory = filePath.substring(0, filePath.lastIndexOf(File.separator));
			File loggerFile = new File(fileDirectory);
			if (!loggerFile.exists()) {
				if(!loggerFile.mkdirs()){
					System.out.println("Logger file directory error!");
				}
			}
		}else{
			filePath = null;
		}
		return filePath;
	}
	
	/**
	 * 获得一个实例
	 * @return 新的实例
	 */
	public static Formater newInstance() {
		String logTemplate = StaticParam.getLogConfig("LogTemplate",StaticParam.LOG_TEMPLATE);
		return new Formater(logTemplate);
	}
	
	/**
	 * 获取输出流
	 * @return 输出流数组
	 */
	protected static OutputStream[] getOutputStreams(){
		String[] LogTypes = StaticParam.getLogConfig("LogType",StaticParam.LOG_TYPE).split(",");
		String logFile = getFormatedLogFilePath();
		
	
		OutputStream[] outputStreams = new OutputStream[LogTypes.length];
		for (int i = 0; i < LogTypes.length; i++) {
			String logType = LogTypes[i].trim();
			switch (logType) {
			case "STDOUT":
				outputStreams[i] = System.out;
				break;
			case "STDERR":
				outputStreams[i] = System.err;
				break;
			case "FILE":
				try {
					outputStreams[i] = new FileOutputStream(logFile,true);
				} catch (FileNotFoundException e) {
					System.out.println("log file: ["+logFile+"] is not found.\r\n");
				}
				break;
			default:
				break;
			}
		}
		return outputStreams;
		
	}
}
