<!DOCTYPE html>
<%@ page import="weaver.general.Util" %>
<%@ page language="java" contentType="text/html; charset=UTF-8" %> 
<%@ include file="/systeminfo/init_wev8.jsp" %>
<%@ taglib uri="/WEB-INF/weaver.tld" prefix="wea"%>
<jsp:useBean id="RecordSet" class="weaver.conn.RecordSet" scope="page" />
<%
String resourceids = Util.null2String(request.getParameter("resourceids"));
String selectpubitem = Util.null2o(RecordSet.getPropValue("SignPubSelectItemSet", "selectId"));
String sql = "select b.id,b.name,b.defaultvalue from mode_selectitempagedetail b where mainid = "+selectpubitem+" and (cancel is null or cancel !='1') and statelev='1' and pid='0' order by disorder asc,id asc ";
RecordSet.executeSql(sql);
int datacount = RecordSet.getCounts();
%>
<HTML><HEAD>
<LINK REL=stylesheet type=text/css HREF=/css/Weaver_wev8.css>
</HEAD>
<BODY>
<%@ include file="/systeminfo/RightClickMenuConent_wev8.jsp" %>
<%
RCMenu += "{"+SystemEnv.getHtmlLabelName(826,user.getLanguage())+",javascript:onSure(),_top} " ;
RCMenuHeight += RCMenuHeightStep ;
RCMenu += "{"+SystemEnv.getHtmlLabelName(201,user.getLanguage())+",javascript:onClose(),_top} " ;
RCMenuHeight += RCMenuHeightStep ;
// RCMenu += "{"+SystemEnv.getHtmlLabelName(311,user.getLanguage())+",javascript:onclear(),_top} " ;
// RCMenuHeight += RCMenuHeightStep ;
%>
<%@ include file="/systeminfo/RightClickMenu_wev8.jsp" %>
<div class="zDialog_div_content" style="height: 100%!important;">
<jsp:include page="/systeminfo/commonTabHead.jsp">
   <jsp:param name="mouldID" value="wokflow"/>
   <jsp:param name="navName" value='<%=SystemEnv.getHtmlLabelNames("68,132050",user.getLanguage()) %>'/>
</jsp:include>
<table id="topTitle" cellpadding="0" cellspacing="0">
	<tr>
		<td>
		</td>
		<td class="rightSearchSpan" style="text-align:right;">
			<span title="<%=SystemEnv.getHtmlLabelName(23036,user.getLanguage())%>" class="cornerMenu"></span>
		</td>
	</tr>
</table>
<wea:layout>
 <wea:group context="<%=SystemEnv.getHtmlLabelName(132050, user.getLanguage())%>">
 <wea:item attributes="{'isTableList':'true'}">
<FORM NAME=SearchForm STYLE="margin-bottom:0" action="wfsignRadioSet.jsp" method=post>
<TABLE ID=BrowseTable class=ListStyle cellspacing=0 STYLE="margin-top:0">
<TR class=header>
<TH width=50%><span style="display: inline-block;"><input type="checkbox" class="InputStyle"  onclick="SelAll(this)"><%=SystemEnv.getHtmlLabelName( 556 ,user.getLanguage())%></span></TH>
<TH width=50%><%=SystemEnv.getHtmlLabelName(132050,user.getLanguage())%></TH>
</tr>
<%
int i=0;
int rowNum=0;
while(RecordSet.next()){
	String detailid = Util.null2String(RecordSet.getString("id"));
	String name = Util.toScreen(Util.null2String(RecordSet.getString("name")),user.getLanguage());	
	if(i==0){
		i=1;
%>

<TR class=DataLight>
<%
	}else{
		i=0;
%>
<TR class=DataDark>
	<%
	}
	%>
	<TD>
		<input type="checkbox" title="shownode" value="1" name="checkbox_<%=rowNum %>" id="checkbox_<%=rowNum %>" <%if((","+resourceids+",").indexOf(","+detailid+",")!=-1||resourceids.equals("0")){%>checked<%} %>>
		<input type="hidden" id="showname_<%=rowNum %>" name="showname_<%=rowNum %>" value="<%=name %>">
		<input type="hidden" id="detailid_<%=rowNum %>" name="detailid_<%=rowNum %>" value="<%=detailid %>">
	</TD>
	<TD>
	 <%=name %>
	</TD>

</TR>
<%
rowNum++;
}
%>
<input type="hidden" id="rowNum" name="rowNum" value="<%=rowNum%>">
</TABLE>
</FORM>
</wea:item>
</wea:group>
</wea:layout>

<div id="zDialog_div_bottom" class="zDialog_div_bottom">
    <wea:layout needImportDefaultJsAndCss="false">
		<wea:group context=""  attributes="{\"groupDisplay\":\"none\"}">
			<wea:item type="toolbar">
				<input type="button" accessKey=O  id=btnok  value="<%="O-"+SystemEnv.getHtmlLabelName(826,user.getLanguage())%>" id="zd_btn_submit_0" class="zd_btn_submit" onclick="onSure();">
<%-- 		    	<input type="button" accessKey=2  id=btnclear value="<%="2-"+SystemEnv.getHtmlLabelName(311,user.getLanguage())%>" id="zd_btn_submit" class="zd_btn_submit" onclick="onclear();"> --%>
		    	<input type="button" accessKey=T  id=btncancel value="<%="T-"+SystemEnv.getHtmlLabelName(201,user.getLanguage())%>" id="zd_btn_cancle"  class="zd_btn_cancle" onclick="onClose();">
			</wea:item>
		</wea:group>
	</wea:layout>
	<script type="text/javascript">
		jQuery(document).ready(function(){
			resizeDialog(document);
		});
	</script>
</div>
</div>

<script type="text/javascript">
var datacount = <%=datacount%>;

var parentWin = parent.parent.getParentWindow(parent);
var dialog = parent.parent.getDialog(parent);
function SelAll(obj){
  if($(obj).is(":checked")){
	  $("input[title=shownode]").each(function(){
	       changeCheckboxStatus($(this),true);
	  });
  }else{
  	  $("input[title=shownode]").each(function(){
	       changeCheckboxStatus($(this),false);
	  });
  }		  
}

 function onSure(){
	var rowNum=jQuery("#rowNum").val();
	var index = 0;
	var detailid = "";
	var showname = "";
	
	for(i=0;i<rowNum;i++){
		if(document.getElementById("checkbox_"+i).checked){
			index++;
			detailid=detailid+","+document.getElementById("detailid_"+i).value;
			showname=showname+","+document.getElementById("showname_"+i).value;
		}
	}
	
    if(showname!=""){
    	detailid=detailid.substr(1);
    	showname=showname.substr(1);
	}
    
    if(datacount == index || index==0){
    	detailid = "0";
    	showname = "<%=SystemEnv.getHtmlLabelName(332, user.getLanguage())%>";
    }
	
  	var returnjson  = {id:detailid,name:showname} ;
	if(dialog){
		try{
	        dialog.callback(returnjson);
	    }catch(e){}
		try{
	     	dialog.close(returnjson);
	 	}catch(e){}
	}else{  
	    window.parent.returnValue  = returnjson;
	    window.parent.close();
	}     
}


function js_btnclear_onclick(){
	var returnjson = {id:"0",name:"<%=SystemEnv.getHtmlLabelName(332, user.getLanguage())%>"};
	if(dialog){
	    dialog.callback(returnjson);
	}else{  
	    window.parent.returnValue  = returnjson;
	    window.parent.close();
	}     
}

function onclear(){
    js_btnclear_onclick();
}

function onClose()
{
	if(dialog){
	    dialog.close();
	}else{  
		window.parent.close() ;
	}	
}
</script>
</BODY></HTML>