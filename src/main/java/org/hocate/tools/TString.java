package org.hocate.tools;

import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TString {

	/**
	 * 移除字符串最后一个字符
	 * @param source
	 * @return
	 */
	public static String removeLastChar(String source){
		return source.substring(0, source.length()-1);
	}

	/**
	 * 左补齐
	 * @param str
	 * @param len
	 * @param c
	 * @return
	 */
	public static String leftPad(String str,int len,char c){
		StringBuffer sb = new StringBuffer();
		for(int i=0; i<len - str.length(); i++){
			sb.append(c);
		}
		return sb.append(str).toString();
	}
	
	/**
	 * 判断是否是数字字符串
	 * @param numberString
	 * @param radix			进制
	 * @return
	 */
	public static boolean isNumber(String numberString,int radix){
		try{
			Integer.parseInt(numberString, radix);
			return true;
		}
		catch(Exception e){
			return false;
		}
	}
	
	/**
	 * 判断是否是整形数
	 * @param integerString
	 * @return
	 */
	public static boolean isInteger(String integerString){
		if(searchByRegex(integerString, "^-?[1-9]\\d*$").length>0){
			return true;
		}else{
			return false;
		}
	}
	
	/**
	 * 判断是否是浮点数
	 * @param floadString
	 * @return
	 */
	public static boolean isFloat(String floadString){
		if(searchByRegex(floadString, "^-?[1-9]\\d*\\.\\d*|-0\\.\\d*[1-9]\\d*$").length>0){
			return true;
		}else{
			return false;
		}
	}
	
	/**
	 * 正则表达式查找
	 * @param source
	 * @param regex
	 * @return
	 */
	public static String[] searchByRegex(String source,String regex){
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(source);
		Vector<String> result = new Vector<String>();
		while(matcher.find()){
			result.add(matcher.group());
		}
		return result.toArray(new String[0]);
	}
	
	/**
	 * 正则表达式替换
	 * @param source
	 * @param regex
	 * @param replacement
	 * @return
	 */
	public static String replaceByRegex(String source,String regex,String replacement){
		for(String searched : searchByRegex(source,regex)){
			source = source.replaceAll(searched, replacement);
		}
		return source;
	}
	
	/**
	 * 判断字符串空指针或者内容为空
	 * @param str
	 * @return
	 */
	public static boolean isNullOrEmpty(String str){
		if(str==null || str.equals("")){
			return true;
		}else{
			return false;
		}
	}
}