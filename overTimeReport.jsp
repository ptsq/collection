<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ page import="weaver.general.PageIdConst" %>
<%@ page import="weaver.workflow.workflow.WorkflowVersion" %>
<%@ page import="weaver.hrm.company.DepartmentComInfo" %>
<%@ page import="weaver.hrm.company.SubCompanyComInfo" %>
<%@ page import="weaver.hrm.resource.ResourceComInfo" %>
<%@ taglib uri="/WEB-INF/weaver.tld" prefix="wea" %>
<%@ include file="/systeminfo/init_wev8.jsp" %>
<%@ taglib uri="/WEB-INF/tld/browser.tld" prefix="brow" %>
<jsp:useBean id="rs" class="weaver.conn.RecordSet" scope="page"/>
<jsp:useBean id="ResourceComInfo" class="weaver.hrm.resource.ResourceComInfo" scope="page"/>
<%
    // 	23815 流程超时报表
    String titlename = SystemEnv.getHtmlLabelName(23815, user.getLanguage());
    String sqlwhere = "  ";
    String overcolname = "";
//处理到达日期起始
    String begindate = Util.null2String(request.getParameter("begindate"));
    if (begindate.isEmpty() == false) {
        sqlwhere += " and receivedate>='" + begindate + "'";
    }
//处理到达日期截止
    String enddate = Util.null2String(request.getParameter("enddate"));
    if (!enddate.isEmpty()) {
        sqlwhere += " and receivedate<='" + enddate + "'";
    }

    String userid = Util.null2String(request.getParameter("userid"));
    // 超时时长
    String overtimes = Util.null2String(request.getParameter("overtimes"));
    if (overtimes.isEmpty() == false) {
        sqlwhere += " and cast(overtimes as integer)>='" + Util.getIntValue(overtimes) * 3600 + "'";
        // 34219 超过 391 小时
        overcolname = SystemEnv.getHtmlLabelName(34219, user.getLanguage()) + overtimes + SystemEnv.getHtmlLabelName(391, user.getLanguage()) + SystemEnv.getHtmlLabelName(17569, user.getLanguage()) + SystemEnv.getHtmlLabelName(1331, user.getLanguage());
    }else{
        // 17569 未处理流程 1331 数量
        overcolname = SystemEnv.getHtmlLabelName(17569, user.getLanguage()) + SystemEnv.getHtmlLabelName(1331, user.getLanguage());

    }
//处理流程类型
    String lxid = Util.null2String(request.getParameter("lxid"));
    String typename = "";
    if (!lxid.equals("")) {
        rs.executeQuery(" select  typename from  workflow_type  where  id = '" + lxid + "'");
        if (rs.next()) {
            typename += "," + Util.null2String(rs.getString("typename"));
        }
        sqlwhere += " and lxid = '" + lxid + "' ";

    }


//处理流程路径名称
    String lcid = Util.null2String(request.getParameter("lcid"));
    String wfname = "";
    List wfidlst = Util.TokenizerString(lcid, ",");
    String wfids = "";
    for (int x = 0; x < wfidlst.size(); x++) {
        rs.executeQuery(" select  workflowname from  workflow_base  where  id = '" + wfidlst.get(x) + "'");
        if (rs.next()) {
            wfname += "," + Util.null2String(rs.getString("workflowname"));
        }
        String lst = WorkflowVersion.getAllVersionStringByWFIDs(wfidlst.get(x) + "");
        wfids += "," + lst;
    }
    if (!wfname.equals("")) {
        wfname = wfname.substring(1);
    }
    if (!wfids.equals("")) {
        wfids = wfids.substring(1);
        sqlwhere += " and " + "(" + Util.getSubINClause(wfids, "lcid", "in") + ")";
    }

//处理超时人部门
    DepartmentComInfo dcf = new DepartmentComInfo();
    String bmid = Util.null2String(request.getParameter("bmid"));
    String bmname = "";
    List undeptidlst = Util.TokenizerString(bmid, ",");
    for (int x = 0; x < undeptidlst.size(); x++) {
        bmname += "," + dcf.getDepartmentname(undeptidlst.get(x) + "");
    }
    if (!bmname.equals("")) {
        bmname = bmname.substring(1);
    }
    if (!bmid.isEmpty()) {
        sqlwhere += " and " + "(" + Util.getSubINClause(bmid, "bmid", "in") + ")";
    }
// 分部
    String fbid = Util.null2String(request.getParameter("fbid"));
    SubCompanyComInfo scci = new SubCompanyComInfo();
    String fbname = "";
    List fbids = Util.TokenizerString(fbid, ",");
    for (int x = 0; x < fbids.size(); x++) {
        fbname += "," + scci.getSubCompanyname(fbids.get(x) + "");
    }
    if (!fbname.equals("")) {
        fbname = fbname.substring(1);
    }
    if (!fbid.isEmpty()) {
        sqlwhere += " and " + "(" + Util.getSubINClause(fbid, "fbid", "in") + ")";
    }


    String backfields = "  rownum,fbid,bmid,userid,overtimes ";
    String fromsql = " from (select fbid,bmid,userid,count(1) overtimes from workflow_overtimefr where 1=1 " + sqlwhere + " group by  fbid,bmid,userid ) t";

    out.print(" select " + backfields + " " + fromsql);

    String TableString = "" +
            "<table  pageId=\"" + PageIdConst.WF_QUERYWFOVERTIME + "\"  pagesize=\"" + PageIdConst.getPageSize(PageIdConst.WF_QUERYWFOVERTIME, user.getUID()) + "\" tabletype=\"none\">" +
            "<sql backfields=\"" + backfields + "\" showCountColumn=\"false\"  sqlform=\"" + Util.toHtmlForSplitPage(fromsql) + "\" sqlorderby=\"overtimes\" sqlprimarykey=\"rownum\" sqlsortway=\"Desc\" />" +
            "<head>" +
            "           <col width=\"9%\"  text=\"" + SystemEnv.getHtmlLabelName(141, user.getLanguage()) + "\" column=\"fbid\" transmethod=\"weaver.hrm.company.SubCompanyComInfo.getSubCompanynameToLink\" />" +
            "           <col width=\"9%\"  text=\"" + SystemEnv.getHtmlLabelName(124, user.getLanguage()) + "\" column=\"bmid\"    transmethod=\"weaver.hrm.company.DepartmentComInfo.getDepartmentnameToLink\"/>" +
            "           <col width=\"9%\"  text=\"" + SystemEnv.getHtmlLabelName(413, user.getLanguage()) + "\" column=\"userid\"  transmethod=\"weaver.hrm.resource.ResourceComInfo.getMulResourcename1\" />" +
            // 17569 未处理流程 超过 34219
            "           <col width=\"9%\"   text=\"" + overcolname + "\" column=\"overtimes\"  orderkey=\"overtimes\" />" +

            "</head>" +
            "</table>";
    String browserUrl = "";
/*if(creator.equals("")){
  browserUrl = "/systeminfo/BrowserMain.jsp?url=/hrm/resource/MutiResourceBrowser.jsp" ;

}else{
  browserUrl = "/systeminfo/BrowserMain.jsp?url=/hrm/resource/MutiResourceBrowser.jsp?resourceids="+ creator;
}*/

%>
<html>
<head>
    <script language="javascript" src="/js/weaver_wev8.js"></script>
    <link href="/css/Weaver_wev8.css" type=text/css rel=stylesheet>
</head>
<body>
<SCRIPT language="javascript" src="/js/datetime_wev8.js"></script>
<SCRIPT language="javascript" src="/js/JSDateTime/WdatePicker_wev8.js"></script>
<%@ include file="/systeminfo/TopTitle_wev8.jsp" %>
<%@ include file="/systeminfo/RightClickMenuConent_wev8.jsp" %>
<%
    RCMenu += "{" + SystemEnv.getHtmlLabelName(82529, user.getLanguage()) + ",javascript:SubmitToSearch(),_self} ";
    RCMenuHeight += RCMenuHeightStep;
    RCMenu += "{" + SystemEnv.getHtmlLabelName(83079, user.getLanguage()) + ",javascript:_xtable_getExcel(),_self} ";
    RCMenuHeight += RCMenuHeightStep;
    RCMenu += "{" + SystemEnv.getHtmlLabelName(81272, user.getLanguage()) + ",javascript:_xtable_getAllExcel(),_self} ";
    RCMenuHeight += RCMenuHeightStep;
%>
<%@ include file="/systeminfo/RightClickMenu_wev8.jsp" %>
<jsp:include page="/systeminfo/commonTabHead.jsp">
    <jsp:param name="mouldID" value="workflow"/>
    <jsp:param name="navName" value="<%=titlename%>"/>
</jsp:include>
<table id="topTitle" cellpadding="0" cellspacing="0">
    <tr>
        <td></td>
        <td class="rightSearchSpan">
            <input type="button" value=<%=SystemEnv.getHtmlLabelName(197, user.getLanguage())%> class="e8_btn_top"
                   onclick="SubmitToSearch();"/>
            <input type="button" value=<%=SystemEnv.getHtmlLabelName(83079, user.getLanguage())%> class="e8_btn_top"
                   onclick="javascript:_xtable_getExcel();"/>
            <input type="button" value=<%=SystemEnv.getHtmlLabelName(81272, user.getLanguage())%> class="e8_btn_top"
                   onclick="javascript:_xtable_getAllExcel();"/>
            <span title="<%=SystemEnv.getHtmlLabelName(83721,user.getLanguage())%>" class="cornerMenu"></span>
        </td>
    </tr>
</table>
<FORM id=frmmain name=frmmain action=overTimeReport.jsp method=post>
    <input type="hidden" name="pageId" id="pageId" value=<%=PageIdConst.WF_QUERYWFOVERTIME%>/>


    <wea:layout type="4col">
        <wea:group context='<%=SystemEnv.getHtmlLabelName(20331, user.getLanguage())%>'>
            <!-- 类型 -->
            <wea:item><%=SystemEnv.getHtmlLabelName(33234, user.getLanguage())%>
            </wea:item>
            <wea:item>
                <brow:browser viewType="0" name="lxid" browserValue="<%=lxid %>"
                              browserUrl="/systeminfo/BrowserMain.jsp?url=/workflow/workflow/WorkTypeBrowser.jsp"
                              hasInput="true" isSingle="true" hasBrowser="true"
                              isMustInput="1" completeUrl="/data.jsp?type=worktypeBrowser"
                              browserDialogWidth="600px"
                              browserSpanValue="<%=typename %>"></brow:browser>
            </wea:item>
            <!-- 工作流 -->
            <wea:item><%=SystemEnv.getHtmlLabelName(18104, user.getLanguage())%>
            </wea:item>
            <wea:item>
                <brow:browser browserDialogHeight="650px;" viewType="0" name="lcid" browserValue="<%=lcid %>"
                              browserOnClick=""
                              browserUrl="/systeminfo/BrowserMain.jsp?url=/workflow/workflow/MutiWorkflowBrowser.jsp?selectedids="
                              idKey="id" nameKey="name" hasInput="true" width="80%" isSingle="false" hasBrowser="true"
                              isMustInput='1' completeUrl="/data.jsp?type=workflowBrowser"
                              browserSpanValue="<%=wfname%>"></brow:browser>
            </wea:item>

            <!-- 分部 -->
            <wea:item><%=SystemEnv.getHtmlLabelName(141, user.getLanguage())%>
            </wea:item>
            <wea:item>
                <brow:browser viewType="0" name="fbid" browserValue='<%=fbid %>' browserOnClick=""
                              browserUrl="/systeminfo/BrowserMain.jsp?url=/hrm/company/MutiSubCompanyBrowser.jsp?selectedids="
                              hasInput="true" isSingle="false" hasBrowser="true" isMustInput='1'
                              completeUrl="/data.jsp?type=164" width="80%"
                              browserSpanValue='<%=fbname %>'> </brow:browser>
            </wea:item>

            <!-- 部门 -->
            <wea:item><%=SystemEnv.getHtmlLabelName(124, user.getLanguage())%>
            </wea:item>
            <wea:item>
                <brow:browser viewType="0" name="bmid" browserValue='<%=bmid %>' browserOnClick=""
                              browserUrl="/systeminfo/BrowserMain.jsp?url=/hrm/company/MutiDepartmentBrowser.jsp?selectedids="
                              hasInput="true" isSingle="false" hasBrowser="true" isMustInput='1'
                              completeUrl="/data.jsp?type=4" width="80%"
                              browserSpanValue='<%=bmname %>'> </brow:browser>
            </wea:item>

            <!-- 日期 97 -->
            <wea:item><%=SystemEnv.getHtmlLabelName(97, user.getLanguage())%>
            </wea:item>
            <wea:item>
						<span class="wuiDateSpan" selectId="recievedateselect" selectValue="">
							<input class=wuiDateSel type="hidden" id="begindate" name="begindate"
                                   value="<%=begindate%>">
							<input class=wuiDateSel type="hidden" id="enddate" name="enddate" value="<%=enddate%>">
						</span>
            </wea:item>
            <!-- 超时小时数 130278 -->
            <wea:item><%=SystemEnv.getHtmlLabelName(130278, user.getLanguage())%>
            </wea:item>
            <wea:item>
						<input type="text" name="overtimes" id="overtimes" value="<%=overtimes%>" />
            </wea:item>

        </wea:group>
        <wea:group context='<%=SystemEnv.getHtmlLabelNames("320", user.getLanguage())%>'>
            <wea:item attributes="{'colspan':'full','isTableList':'true'}">
                <wea:SplitPageTag tableInstanceId="" tableString="<%=TableString%>" mode="run"/>
            </wea:item>
        </wea:group>
    </wea:layout>
</FORM>
</body>
<script type="text/javascript">
    function SubmitToSearch() {
        frmmain.submit();
    }
</script>
</html>
