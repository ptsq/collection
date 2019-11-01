
<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@page import="weaver.workflow.workflow.WorkflowVersion"%>

<%@ taglib uri="/WEB-INF/weaver.tld" prefix="wea"%><%--added by xwj for td2023 on 2005-05-20--%>
<%@ taglib uri="/browserTag" prefix="brow"%>
<%@ include file="/systeminfo/init_wev8.jsp" %>

<%@ page import="weaver.general.Util" %>


<%@ page import="java.util.*" %>
<%@ page import="weaver.conn.RecordSetDataSource" %>
<%@ page import="weaver.workflow.request.SyncData" %>
<%@ page import="java.sql.ResultSet" %>
<%@ page import="java.sql.PreparedStatement" %>
<%@ page import="javax.sql.DataSource" %>

<jsp:useBean id="RecordSet" class="weaver.conn.RecordSet" scope="page" />
<jsp:useBean id="WorkflowComInfo" class="weaver.workflow.workflow.WorkflowComInfo" scope="page" />
<jsp:useBean id="ResourceComInfo" class="weaver.hrm.resource.ResourceComInfo" scope="page" />

<jsp:useBean id="DepartmentComInfo" class="weaver.hrm.company.DepartmentComInfo" scope="page"/>

<jsp:useBean id="BrowserComInfo" class="weaver.workflow.field.BrowserComInfo" scope="page"/>
<jsp:useBean id="SubCompanyComInfo" class="weaver.hrm.company.SubCompanyComInfo" scope="page" />

<%
	String beginDate = Util.null2String(request.getParameter("beginDate"));
	String endDate = Util.null2String(request.getParameter("endDate"));
	String requestid = Util.null2String(request.getParameter("requestid"));
	String exec = Util.null2String(request.getParameter("exec"));
	RecordSet.writeLog("beginDate=" + beginDate + ",endDate=" + endDate + ",requestid=" + requestid);

	// 逻辑执行
	if(exec.equals("1")){
		if(beginDate.isEmpty() && endDate.isEmpty() && requestid.isEmpty()){

			out.clear();
			out.write("条件不能全部为空！");
			out.close();
			return;

		}else{
			//System.out.println("22222222");
			String dsname = "csxt";
			weaver.interfaces.datasource.DataSource ds = ( weaver.interfaces.datasource.DataSource) StaticObj.getServiceByFullname(("datasource." + dsname), DataSource.class);
			java.sql.Connection conn = ds.getConnection();
			String sql = "select * from workflow_requestbase where 1=1 ";
			if (!requestid.isEmpty()) {
				sql += " and requestid='"+requestid+"' ";
			}
			if(!beginDate.isEmpty()){
			    sql += " and createdate>='"+beginDate+"' ";
			}
			if (!endDate.isEmpty()) {
				sql += " and createdate<='" + endDate + "' ";
			}
			RecordSet.writeLog("查询符合条件的请求=" + sql);
			// 执行requestbase
			//rsds.executeSql("select * from workflow_requestbase where 1=1 " + sql);
			PreparedStatement pstmt = conn.prepareStatement(sql);
			ResultSet resultSet = pstmt.executeQuery();
			SyncData sd = new SyncData();
			while (resultSet.next()) {

				String requestid1 = Util.null2String(resultSet.getString("requestid"));
				RecordSet.writeLog("requestid=" + requestid1);
				sd.syncData(requestid1,dsname);


			}





			out.clear();
			out.write("同步完成，请核查数据！");
			out.close();
			return ;
		}
	}



%>
<html>
	<head>
		<link href="/css/Weaver_wev8.css" type="text/css" rel="stylesheet">
		<script language="javascript" src="/js/weaver_wev8.js"></script>
		<script language="javascript" src="/js/datetime_wev8.js"></script>
		<script language="javascript" src="/js/selectDateTime_wev8.js"></script>
		<script language="javascript" src="/js/JSDateTime/WdatePicker_wev8.js"></script>
		<script type="text/javascript">
            $(function(){
                $('.e8_box').Tabs({
                    getLine:1,
                    mouldID:"<%= MouldIDConst.getID("workflow")%>",
                    //iframe:"tabcontentframe",
                    staticOnLoad:true,
                    objName:"流程同步"
                });


            });
            function resetCondtion(selector){
                jQuery("#form1 input[type='text']").val('');
                jQuery("#form1 input[type='hidden']").val('');
            }

            function submitForm(){
				// 异步提交
				var b = jQuery("#beginDate").val();
				var e = jQuery("#endDate").val();
				var r = jQuery("#requestid").val();
				//alert(b+","+e+","+r)
				jQuery.ajax({
					async:false,
					url: "workflowCopy.jsp",
					dataType:"text",
					data:{"beginDate":b,"endDate":e,"requestid":r,"exec":1},
                    beforeSend:function(xhr){
                        try{
                            e8showAjaxTips("<%=SystemEnv.getHtmlLabelName(84024,user.getLanguage())%>",true);
                        }catch(e){}
                    },complete:function(xhr){
                        e8showAjaxTips("",false);
                    },
					success:function(data){
						alert(data);
					}
				})
            }


		</script>
	</head>


	<body>
	<jsp:include page="/systeminfo/commonTabHead.jsp">
		<jsp:param name="mouldID" value="wokflow"/>
		<jsp:param name="navName" value='流程同步'/>
	</jsp:include>
<form id="form1" >
<wea:layout type="4col" attributes="{'expandAllGroup':'true'}">

	<wea:group context='同步流程数据'>


		<wea:item><%=SystemEnv.getHtmlLabelName(722, user.getLanguage())%></wea:item>
		<wea:item  attributes="{\"colspan\":\"1\"}">
						<span class="wuiDateSpan" selectId="recievedateselect" selectValue="">
							<input class=wuiDateSel type="hidden" id="beginDate" name="beginDate" value="">
							<input class=wuiDateSel type="hidden" id="endDate" name="endDate" value="">
						</span>
		</wea:item>
		<wea:item><%=SystemEnv.getHtmlLabelName(18376, user.getLanguage())%></wea:item>
		<wea:item  attributes="{\"colspan\":\"1\"}">
			<input type="text" id="requestid" name="requestid" onkeyup="value=value.replace(/[^\d]/g,'')" style='width:80%' value="">
		</wea:item>

	</wea:group>
	<wea:group context="">
		<wea:item type="toolbar">
			<input type="button" value="<%=SystemEnv.getHtmlLabelName(826, user.getLanguage())%>" class="e8_btn_submit" onclick="submitForm();"/>
			<span class="e8_sep_line">|</span>
			<input type="button"  value="<%=SystemEnv.getHtmlLabelName(2022, user.getLanguage())%>" class="e8_btn_cancel" onclick="resetCondtion();">

		</wea:item>
	</wea:group>


</wea:layout>
</form>
		
	</body>

</html>
