package weaver.workflow.request;


import weaver.conn.ConnStatement;
import weaver.conn.RecordSet;
import weaver.conn.RecordSetDataSource;
import weaver.general.BaseBean;
import weaver.general.Util;
import weaver.workflow.workflow.WorkflowBillComInfo;
import weaver.workflow.workflow.WorkflowComInfo;
import weaver.workflow.workflow.WorkflowVersion;

import java.util.*;

/**
 * 两套系统流程数据同步功能
 */
public class SyncData extends BaseBean {
    private static RequestIdUpdate requestIdUpdate = new RequestIdUpdate();

    /**
     *
     * @param reqid 测试环境requestid
     * @param dsname 测试环境ds
     */
    public void syncData(String reqid, String dsname) {
        RecordSetDataSource rsds = new RecordSetDataSource(dsname);    // 测试系统ds
        RecordSet rs = new RecordSet();
        boolean upsuccess = false;
        int requestid = -1;
        String workflowid = "";
        int mainid = 0;
        // 取得基础数据信息
        rsds.execute("select workflowid from workflow_requestbase where requestid= " + reqid);
        if (rsds.next()) {
            String oldrequestid = Util.null2String(rsds.getString("requestid"));
            workflowid = Util.null2String(rsds.getString("workflowid"));
            // 校验workflowid是否存在
            rs.executeQuery("select 1 from workflow_base where id=?", workflowid);
            if (!rs.next()) {
                writeLog("本系统中没有该流程，退出！workflowid=" + workflowid);
                return;
            }
            // 取得本系统是否有当前活动的workflowid
            workflowid = WorkflowVersion.getActiveVersionWFID(workflowid);

            WorkflowComInfo wci = new WorkflowComInfo();
            String isBill = wci.getIsBill(workflowid);
            String formId = wci.getFormId(workflowid);
            WorkflowBillComInfo wbci = new WorkflowBillComInfo();
            String tablename = wbci.getTablename(formId);
            if ("1".equals(isBill)) {
                requestIdUpdate.setBilltablename(tablename);
            }
            //requestid 和 billid 一起返回，避免同一时间提交的流程，billid出错
            int rvalue[] = requestIdUpdate.getRequestNewId(tablename);
            requestid = rvalue[0];
            mainid = rvalue[1];
            if (requestid == -1) {
                writeLog("old请求=" + oldrequestid + ",创建新请求失败！");
                return;
            }
        }
        writeLog("新产生的请求=" + requestid + ",billid="+mainid+",workflowid=" + workflowid + ",开始执行同步>>>>>>");

        // 取得requestbase所有字段,置于最前，便于查询失败记录。
        rsds.execute("select * from workflow_requestbase where requestid= " + reqid);
        if (rsds.next()) {
            String oldrequestid = Util.null2String(rsds.getString("requestid"));
            workflowid = Util.null2String(rsds.getString("workflowid"));
            String lastnodeid = Util.null2String(rsds.getString("lastnodeid"));
            String lastnodetype = Util.null2String(rsds.getString("lastnodetype"));
            String currentnodeid = Util.null2String(rsds.getString("currentnodeid"));
            String currentnodetype = Util.null2String(rsds.getString("currentnodetype"));
            String status = Util.null2String(rsds.getString("status"));
            String passedgroups = Util.null2String(rsds.getString("passedgroups"));
            String totalgroups = Util.null2String(rsds.getString("totalgroups"));
            String requestname = Util.null2String(rsds.getString("requestname"));
            String creater = Util.null2String(rsds.getString("creater"));
            String createdate = Util.null2String(rsds.getString("createdate"));
            String createtime = Util.null2String(rsds.getString("createtime"));
            String lastoperator = Util.null2String(rsds.getString("lastoperator"));
            String lastoperatedate = Util.null2String(rsds.getString("lastoperatedate"));
            String lastoperatetime = Util.null2String(rsds.getString("lastoperatetime"));
            String deleted = Util.null2String(rsds.getString("deleted"));
            String creatertype = Util.null2String(rsds.getString("creatertype"));
            String lastoperatortype = Util.null2String(rsds.getString("lastoperatortype"));
            String nodepasstime = Util.null2String(rsds.getString("nodepasstime"));
            String nodelefttime = Util.null2String(rsds.getString("nodelefttime"));
            String docids = Util.null2String(rsds.getString("docids"));
            String crmids = Util.null2String(rsds.getString("crmids"));
            String hrmids = Util.null2String(rsds.getString("hrmids"));
            String prjids = Util.null2String(rsds.getString("prjids"));
            String cptids = Util.null2String(rsds.getString("cptids"));
            String requestlevel = Util.null2String(rsds.getString("requestlevel"));
            String requestmark = Util.null2String(rsds.getString("requestmark"));
            String messageType = Util.null2String(rsds.getString("messageType"));
            String mainRequestId = Util.null2String(rsds.getString("mainRequestId"));
            String currentstatus = Util.null2String(rsds.getString("currentstatus"));
            String laststatus = Util.null2String(rsds.getString("laststatus"));
            String ismultiprint = Util.null2String(rsds.getString("ismultiprint"));
            String chatsType = Util.null2String(rsds.getString("chatsType"));
            String ecology_pinyin_search = Util.null2String(rsds.getString("ecology_pinyin_search"));
            String requestnamenew = Util.null2String(rsds.getString("requestnamenew"));
            String formsignaturemd5 = Util.null2String(rsds.getString("formsignaturemd5"));
            String dataaggregated = Util.null2String(rsds.getString("dataaggregated"));

            // 更新requestbase
            String insql = "insert into workflow_requestbase(requestid,workflowid,lastnodeid,lastnodetype,currentnodeid,currentnodetype,status,passedgroups,totalgroups,requestname,creater,createdate,createtime,lastoperator,lastoperatedate,lastoperatetime,deleted,creatertype,lastoperatortype,nodepasstime,nodelefttime,docids,crmids,hrmids,prjids,cptids,requestlevel,requestmark,messageType,mainRequestId,currentstatus,laststatus,ismultiprint,chatsType,ecology_pinyin_search,requestnamenew,formsignaturemd5,dataaggregated) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            upsuccess = rs.executeUpdate(insql, requestid, workflowid, lastnodeid, lastnodetype, currentnodeid, currentnodetype, status, passedgroups, totalgroups, requestname, creater, createdate, createtime, lastoperator, lastoperatedate, lastoperatetime, deleted, creatertype, lastoperatortype, nodepasstime, nodelefttime, docids, crmids, hrmids, prjids, cptids, requestlevel, requestmark, messageType, mainRequestId, currentstatus, laststatus, ismultiprint, chatsType, ecology_pinyin_search, requestnamenew, formsignaturemd5, dataaggregated);
        }
        if (upsuccess) {
            writeLog("新请求=" + requestid + ",更新requestbase成功！");
        } else {
            writeLog("更新失败，oldrequestid=" + reqid + ",程序退出！");
            return;
        }


        // 更新workflow_form 提前，因表单写入这个表
        rsds.execute("select * from workflow_form where requestid= " + reqid);
        while (rsds.next()) {

            String billformid = Util.null2o(rsds.getString("billformid"));
            String billid = Util.null2o(rsds.getString("billid"));
            String totaltime = Util.null2o(rsds.getString("totaltime"));
            String department = Util.null2o(rsds.getString("department"));
            String relatedcustomer = Util.null2o(rsds.getString("relatedcustomer"));
            String relatedresource = Util.null2o(rsds.getString("relatedresource"));
            String relateddocument = Util.null2o(rsds.getString("relateddocument"));
            String relatedrequest = Util.null2o(rsds.getString("relatedrequest"));
            String userdepartment = Util.null2o(rsds.getString("userdepartment"));
            String startrailwaystation = Util.null2o(rsds.getString("startrailwaystation"));
            String subject = Util.null2o(rsds.getString("subject"));
            String hotellevel = Util.null2o(rsds.getString("hotellevel"));
            String integer1 = Util.null2o(rsds.getString("integer1"));
            String desc1 = Util.null2o(rsds.getString("desc1"));
            String desc2 = Util.null2o(rsds.getString("desc2"));
            String desc3 = Util.null2o(rsds.getString("desc3"));
            String desc4 = Util.null2o(rsds.getString("desc4"));
            String desc5 = Util.null2o(rsds.getString("desc5"));
            String desc6 = Util.null2o(rsds.getString("desc6"));
            String desc7 = Util.null2o(rsds.getString("desc7"));
            String integer2 = Util.null2o(rsds.getString("integer2"));
            String reception_important = Util.null2o(rsds.getString("reception_important"));
            String check2 = Util.null2o(rsds.getString("check2"));
            String check3 = Util.null2o(rsds.getString("check3"));
            String check4 = Util.null2o(rsds.getString("check4"));
            String textvalue1 = Util.null2o(rsds.getString("textvalue1"));
            String textvalue2 = Util.null2o(rsds.getString("textvalue2"));
            String textvalue3 = Util.null2o(rsds.getString("textvalue3"));
            String textvalue4 = Util.null2o(rsds.getString("textvalue4"));
            String textvalue5 = Util.null2o(rsds.getString("textvalue5"));
            String textvalue6 = Util.null2o(rsds.getString("textvalue6"));
            String textvalue7 = Util.null2o(rsds.getString("textvalue7"));
            String softwaregetway = Util.null2o(rsds.getString("softwaregetway"));
            String decimalvalue1 = Util.null2o(rsds.getString("decimalvalue1"));
            String decimalvalue2 = Util.null2o(rsds.getString("decimalvalue2"));
            String manager = Util.null2o(rsds.getString("manager"));
            String jobtitle = Util.null2o(rsds.getString("jobtitle"));
            String jobtitle2 = Util.null2o(rsds.getString("jobtitle2"));
            String document = Util.null2o(rsds.getString("document"));
            String Customer = Util.null2o(rsds.getString("Customer"));
            String Project = Util.null2o(rsds.getString("Project"));
            String resource_n = Util.null2o(rsds.getString("resource_n"));
            String item = Util.null2o(rsds.getString("item"));
            String request = Util.null2o(rsds.getString("request"));
            String mutiresource = Util.null2o(rsds.getString("mutiresource"));
            String muticustomer = Util.null2o(rsds.getString("muticustomer"));
            String remark = Util.null2o(rsds.getString("remark"));
            String description = Util.null2o(rsds.getString("description"));
            String begindate = Util.null2o(rsds.getString("begindate"));
            String begintime = Util.null2o(rsds.getString("begintime"));
            String enddate = Util.null2o(rsds.getString("enddate"));
            String endtime = Util.null2o(rsds.getString("endtime"));
            String totaldays = Util.null2o(rsds.getString("totaldays"));
            String check1 = Util.null2o(rsds.getString("check1"));
            String amount = Util.null2o(rsds.getString("amount"));
            String startairport = Util.null2o(rsds.getString("startairport"));
            String airways = Util.null2o(rsds.getString("airways"));
            String payoptions = Util.null2o(rsds.getString("payoptions"));
            String expresstype = Util.null2o(rsds.getString("expresstype"));
            String jtgj = Util.null2o(rsds.getString("jtgj"));
            String absencetype = Util.null2o(rsds.getString("absencetype"));
            String zc = Util.null2o(rsds.getString("zc"));
            String zczl = Util.null2o(rsds.getString("zczl"));
            String fwcp = Util.null2o(rsds.getString("fwcp"));
            String muticareer = Util.null2o(rsds.getString("muticareer"));
            String date1 = Util.null2o(rsds.getString("date1"));
            String date2 = Util.null2o(rsds.getString("date2"));
            String date3 = Util.null2o(rsds.getString("date3"));
            String date4 = Util.null2o(rsds.getString("date4"));
            String date5 = Util.null2o(rsds.getString("date5"));
            String date6 = Util.null2o(rsds.getString("date6"));
            String time1 = Util.null2o(rsds.getString("time1"));
            String time2 = Util.null2o(rsds.getString("time2"));
            String time3 = Util.null2o(rsds.getString("time3"));
            String time4 = Util.null2o(rsds.getString("time4"));
            String time5 = Util.null2o(rsds.getString("time5"));
            String time6 = Util.null2o(rsds.getString("time6"));
            String resource1 = Util.null2o(rsds.getString("resource1"));
            String date_n = Util.null2o(rsds.getString("date_n"));
            String relatmeeting = Util.null2o(rsds.getString("relatmeeting"));
            String itservice = Util.null2o(rsds.getString("itservice"));
            String cplx = Util.null2o(rsds.getString("cplx"));
            String gzlx = Util.null2o(rsds.getString("gzlx"));
            String insql = "insert into workflow_form(requestid,billformid,billid,totaltime,department,relatedcustomer,relatedresource,relateddocument,relatedrequest,userdepartment,startrailwaystation,subject,hotellevel,integer1,desc1,desc2,desc3,desc4,desc5,desc6,desc7,integer2,reception_important,check2,check3,check4,textvalue1,textvalue2,textvalue3,textvalue4,textvalue5,textvalue6,textvalue7,softwaregetway,decimalvalue1,decimalvalue2,manager,jobtitle,jobtitle2,document,Customer,Project,resource_n,item,request,mutiresource,muticustomer,remark,description,begindate,begintime,enddate,endtime,totaldays,check1,amount,startairport,airways,payoptions,expresstype,jtgj,absencetype,zc,zczl,fwcp,muticareer,date1,date2,date3,date4,date5,date6,time1,time2,time3,time4,time5,time6,resource1,date_n,relatmeeting,itservice,cplx,gzlx) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            upsuccess = rs.executeUpdate(insql, requestid, billformid, billid, totaltime, department, relatedcustomer, relatedresource, relateddocument, relatedrequest, userdepartment, startrailwaystation, subject, hotellevel, integer1, desc1, desc2, desc3, desc4, desc5, desc6, desc7, integer2, reception_important, check2, check3, check4, textvalue1, textvalue2, textvalue3, textvalue4, textvalue5, textvalue6, textvalue7, softwaregetway, decimalvalue1, decimalvalue2, manager, jobtitle, jobtitle2, document, Customer, Project, resource_n, item, request, mutiresource, muticustomer, remark, description, begindate, begintime, enddate, endtime, totaldays, check1, amount, startairport, airways, payoptions, expresstype, jtgj, absencetype, zc, zczl, fwcp, muticareer, date1, date2, date3, date4, date5, date6, time1, time2, time3, time4, time5, time6, resource1, date_n, relatmeeting, itservice, cplx, gzlx);
            if (upsuccess) {
                writeLog("新请求=" + requestid + ",更新 workflow_form 成功");
            } else {
                writeLog("更新 workflow_form 失败，oldrequestid=" + reqid + ",newrequestid="+requestid+",程序执行回滚>>>");
                delData(requestid);
                writeLog("回滚完成，程序退出<<<");
                break;
            }
        }

        /**** 更新表单数据 ****/
        WorkflowComInfo wci = new WorkflowComInfo();
        String formId = wci.getFormId(workflowid);
        String isBill = wci.getIsBill(workflowid);
        String tablename = "";
        if(isBill.equals("1")) {    // 只更新单据的，表单在workflow_form已经处理
            WorkflowBillComInfo wbci = new WorkflowBillComInfo();
            tablename = wbci.getTablename(formId);

            String mainfields = "";
            LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();

            if (isBill.equals("1")) {
                rsds.executeProc("workflow_billfield_Select", formId + "");
                while (rsds.next()) {
                    String viewtype = Util.null2String(rsds.getString("viewtype"));   // 如果是单据的从表字段,不进行操作
                    if (viewtype.equals("1")) continue;
                    String fieldname = Util.null2String(rsds.getString("fieldname"));
                    mainfields += "," + fieldname;
                    map.put(fieldname, "");
                }
            }

            // 验证两套系统是否字段一致
            if (!mainfields.isEmpty()) {
                mainfields = mainfields.substring(1);
                upsuccess = rs.executeQuery("select " + mainfields + " from " + tablename + " where requestid=?", requestid);
                if (!upsuccess) {
                    writeLog("两套系统的表单字段不一致！退出同步，oldrequestid=" + reqid + ",newrequestid=" + requestid + ",程序执行回滚>>>");
                    delData(requestid);
                    delTableData(requestid, tablename);
                    writeLog("回滚完成，程序退出<<<");
                    return;
                }
            }

            writeLog("表单=" + tablename + ",查询到的字段=" + map.toString());
            if (map.size() == 0 && isBill.equals("1")) {
                writeLog("表" + tablename + ",没找到字段。old请求=" + reqid);
                return;
            }
            // 查询主表数据
            String s = "select " + mainfields + " from " + tablename + " where requestid=" + reqid;
            writeLog("查询表单中所有字段数据=" + s);
            rsds.executeSql(s);
            rsds.next();

            String upsql = " update " + tablename + " set ";// 更新主表数据
            for (Map.Entry<String, String> entry : map.entrySet()) {
                entry.setValue(rsds.getString(entry.getKey())); // 字段赋值
                upsql += entry.getKey() + "=?,";

            }
            if (upsql.endsWith(",")) {
                upsql = upsql.substring(0, upsql.length() - 1);
            }
            upsql += " where requestid=" + requestid;
            writeLog("更新主表数据=" + upsql + ",对应值=" + map.toString());
            ConnStatement cstate = null;
            try {
                cstate = new ConnStatement();
                cstate.setStatementSql(upsql);
                int i = 1;
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    cstate.setString(i, Util.null2String(entry.getValue()).trim());// 预编译模式setvalue
                    i++;
                }

                cstate.executeUpdate();
            } catch (Exception e) {
                writeLog(e);
                upsuccess = false;
            } finally {
                if (cstate != null) {
                    cstate.close();
                }
            }
            if (!upsuccess) {
                writeLog("字段赋值失败，oldrequestid=" + reqid + ",newrequestid=" + requestid + ",程序执行回滚>>>");
                delData(requestid);
                delTableData(requestid, tablename);
                writeLog("回滚完成，程序退出<<<");
                return;
            }

            // 新增明细表数据 遍历所有明细表
            List<String> olist = new ArrayList<String>();
            rsds.executeSql("select distinct detailtable from workflow_billfield where billid=" + formId + " and viewtype=1");
            while (rsds.next()) {
                olist.add(rsds.getString(1));
            }
            List<String> nlist = new ArrayList<String>();
            rs.executeQuery("select distinct detailtable from workflow_billfield where billid=" + formId + " and viewtype=1");
            while (rs.next()) {
                nlist.add(rs.getString(1));
            }
            for (String str : olist) {
                if (!nlist.contains(str)) {
                    upsuccess = false;
                    break;
                }
            }
            if (!upsuccess) {
                writeLog("两套系统的明细表数量不一致！退出同步。old请求=" + reqid);
                return;
            }
            for (String str : olist) {
                // 取明细表所有字段
                String dfieldsql = "";    // 明细表字段
                Map<String, String> dfmap = new LinkedHashMap<String, String>();
                rs.executeQuery("select fieldname from workflow_billfield where detailtable=?", str);
                while (rs.next()) {
                    String fieldname = rs.getString(1);
                    dfieldsql += "," + fieldname;
                    dfmap.put(fieldname, "");
                }
                // 验证字段是否一致
                if (!dfieldsql.isEmpty()) {
                    dfieldsql = dfieldsql.substring(1);
                    upsuccess = rsds.executeSql("select " + dfieldsql + " from " + str + " where mainid='" + mainid + "'");
                    if (!upsuccess) {
                        writeLog("两套系统的明细表单字段不一致！退出同步。old请求=" + reqid + ",mainid=" + mainid);
                        return;
                    }
                }
                writeLog("明细表单=" + str + ",查询到的字段=" + dfmap.toString());
                if (dfmap.size() == 0) {
                    writeLog("明细表" + str + ",没找到字段。old请求=" + reqid);
                    return;
                }
                // 字段一致，开始赋值

                // 查询明细表数据，多行
                String qdsql = "select " + dfieldsql + " from " + str + " where mainid=(select id from " + tablename + " where requestid=" + reqid + ")";
                writeLog("查询明细表单中所有字段数据=" + qdsql);
                rsds.executeSql(qdsql);
                while (rsds.next()) {
                    String firstinsql = "insert into " + str + "(mainid) values(?)";
                    upsuccess = rs.executeUpdate(firstinsql, mainid);
                    if (!upsuccess) {
                        writeLog("先插入一行数据，后面的数据执行update操作，更新失败，oldrequestid=" + reqid + ",newrequestid=" + requestid + ",程序执行回滚>>>");
                        delData(requestid);
                        delTableData(requestid, tablename);  // 明细表数据可以不用清理，不影响
                        writeLog("回滚完成，程序退出<<<");
                        return;
                    }
                    rs.executeQuery("select max(id) from " + str + " where mainid=?", mainid);
                    rs.next();
                    String maxid = rs.getString(1); // 取到插入的最大值

                    String updetailsql = " update " + str + " set ";// 更新主表数据
                    for (Map.Entry<String, String> entry : dfmap.entrySet()) {
                        entry.setValue(rsds.getString(entry.getKey())); // 字段赋值
                        updetailsql += entry.getKey() + "=?,";
                    }
                    if (updetailsql.endsWith(",")) {
                        updetailsql = updetailsql.substring(0, updetailsql.length() - 1);
                    }
                    updetailsql += " where id=" + maxid + " and mainid=" + mainid;
                    writeLog("写入明细数据=" + updetailsql + ",对应字段key和val=" + dfmap.toString());
                    try {
                        cstate = new ConnStatement();
                        cstate.setStatementSql(updetailsql);
                        int i = 1;
                        for (Map.Entry<String, String> entry : dfmap.entrySet()) {
                            cstate.setString(i, Util.null2String(entry.getValue()).trim());// 预编译模式setvalue
                            i++;
                        }
                        cstate.executeUpdate();
                        writeLog("更新明细表数据=" + updetailsql + ",对应值=" + map.toString());
                    } catch (Exception e) {
                        writeLog("明细执行异常=" + e + " oldrequestid=" + reqid + ",newrequestid=" + requestid + ",程序执行回滚>>>");
                        delData(requestid);
                        delTableData(requestid, tablename);  // 明细表数据可以不用清理，不影响
                        writeLog("回滚完成，程序退出<<<");
                        return;
                    } finally {
                        if (cstate != null) {
                            cstate.close();
                        }
                    }

                }
            }
        }
        /**** 更新表单数据 end ****/

        // 更新currentoperator
        rsds.execute("select * from workflow_currentoperator where requestid= " + reqid);
        while (rsds.next()) {
            String userid = Util.null2String(rsds.getString("userid"));
            String groupid = Util.null2String(rsds.getString("groupid"));
            String workflowtype = Util.null2String(rsds.getString("workflowtype"));
            String isremark = Util.null2String(rsds.getString("isremark"));
            String usertype = Util.null2String(rsds.getString("usertype"));
            String nodeid = Util.null2String(rsds.getString("nodeid"));
            String agentorbyagentid = Util.null2String(rsds.getString("agentorbyagentid"));
            String agenttype = Util.null2String(rsds.getString("agenttype"));
            String showorder = Util.null2String(rsds.getString("showorder"));
            String receivedate = Util.null2String(rsds.getString("receivedate"));
            String receivetime = Util.null2String(rsds.getString("receivetime"));
            String viewtype = Util.null2String(rsds.getString("viewtype"));
            String iscomplete = Util.null2String(rsds.getString("iscomplete"));
            String islasttimes = Util.null2String(rsds.getString("islasttimes"));
            String operatedate = Util.null2String(rsds.getString("operatedate"));
            String operatetime = Util.null2String(rsds.getString("operatetime"));
            String groupdetailid = Util.null2String(rsds.getString("groupdetailid"));
            String isreminded = Util.null2String(rsds.getString("isreminded"));
            String isprocessed = Util.null2String(rsds.getString("isprocessed"));
            String wfreminduser = Util.null2String(rsds.getString("wfreminduser"));
            String wfusertypes = Util.null2String(rsds.getString("wfusertypes"));
            String preisremark = Util.null2String(rsds.getString("preisremark"));
            String isreject = Util.null2String(rsds.getString("isreject"));
            String needwfback = Util.null2String(rsds.getString("needwfback"));
            String lastisremark = Util.null2String(rsds.getString("lastisremark"));
            String isreminded_csh = Util.null2String(rsds.getString("isreminded_csh"));
            String wfreminduser_csh = Util.null2String(rsds.getString("wfreminduser_csh"));
            String wfusertypes_csh = Util.null2String(rsds.getString("wfusertypes_csh"));
            String handleforwardid = Util.null2String(rsds.getString("handleforwardid"));
            String takisremark = Util.null2String(rsds.getString("takisremark"));
            String lastRemindDatetime = Util.null2String(rsds.getString("lastRemindDatetime"));
            String opdatetime = Util.null2String(rsds.getString("opdatetime"));
            String id = Util.null2String(rsds.getString("id"));

            String insql = "insert into workflow_currentoperator(requestid,userid,groupid,workflowid,workflowtype,isremark,usertype,nodeid,agentorbyagentid,agenttype,showorder,receivedate,receivetime,viewtype,iscomplete,islasttimes,operatedate,operatetime,groupdetailid,isreminded,isprocessed,wfreminduser,wfusertypes,preisremark,isreject,needwfback,lastisremark,isreminded_csh,wfreminduser_csh,wfusertypes_csh,handleforwardid,takisremark,lastRemindDatetime,opdatetime) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            upsuccess = rs.executeUpdate(insql, requestid, userid, groupid, workflowid, workflowtype, isremark, usertype, nodeid, agentorbyagentid, agenttype, showorder, receivedate, receivetime, viewtype, iscomplete, islasttimes, operatedate, operatetime, groupdetailid, isreminded, isprocessed, wfreminduser, wfusertypes, preisremark, isreject, needwfback, lastisremark, isreminded_csh, wfreminduser_csh, wfusertypes_csh, handleforwardid, takisremark, lastRemindDatetime, opdatetime);
            if (upsuccess) {
                writeLog("新请求=" + requestid + ",更新 workflow_currentoperator 成功,old主键=" + id);
            } else {
                writeLog("更新 workflow_currentoperator 失败，oldrequestid=" + reqid + ",newrequestid="+requestid+",程序执行回滚>>>");
                delData(requestid);
                writeLog("回滚完成，程序退出<<<");
                break;
            }

        }

        // 更新requestlog
        rsds.execute("select * from workflow_requestlog where requestid= " + reqid);
        while (rsds.next()) {
            String logid = Util.null2String(rsds.getString("logid"));
            String nodeid = Util.null2String(rsds.getString("nodeid"));
            String logtype = Util.null2String(rsds.getString("logtype"));
            String operatedate = Util.null2String(rsds.getString("operatedate"));
            String operatetime = Util.null2String(rsds.getString("operatetime"));
            String operator = Util.null2String(rsds.getString("operator"));
            String remark = Util.null2String(rsds.getString("remark"));
            String clientip = Util.null2String(rsds.getString("clientip"));
            String operatortype = Util.null2String(rsds.getString("operatortype"));
            String destnodeid = Util.null2String(rsds.getString("destnodeid"));
            String receivedPersons = Util.null2String(rsds.getString("receivedPersons"));
            String showorder = Util.null2String(rsds.getString("showorder"));
            String agentorbyagentid = Util.null2String(rsds.getString("agentorbyagentid"));
            String agenttype = Util.null2String(rsds.getString("agenttype"));
            String annexdocids = Util.null2String(rsds.getString("annexdocids"));
            String requestLogId = Util.null2String(rsds.getString("requestLogId"));
            String operatorDept = Util.null2String(rsds.getString("operatorDept"));
            String signdocids = Util.null2String(rsds.getString("signdocids"));
            String signworkflowids = Util.null2String(rsds.getString("signworkflowids"));
            String isMobile = Util.null2String(rsds.getString("isMobile"));
            String HandWrittenSign = Util.null2String(rsds.getString("HandWrittenSign"));
            String SpeechAttachment = Util.null2String(rsds.getString("SpeechAttachment"));
            String receivedpersonids = Util.null2String(rsds.getString("receivedpersonids"));
            String remarklocation = Util.null2String(rsds.getString("remarklocation"));
            String isSubmitDirect = Util.null2String(rsds.getString("isSubmitDirect"));
            String insql = "insert into workflow_requestlog(requestid,workflowid,nodeid,logtype,operatedate,operatetime,operator,remark,clientip,operatortype,destnodeid,receivedPersons,showorder,agentorbyagentid,agenttype,annexdocids,requestLogId,operatorDept,signdocids,signworkflowids,isMobile,HandWrittenSign,SpeechAttachment,receivedpersonids,remarklocation,isSubmitDirect) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            upsuccess = rs.executeUpdate(insql, requestid, workflowid, nodeid, logtype, operatedate, operatetime, operator, remark, clientip, operatortype, destnodeid, receivedPersons, showorder, agentorbyagentid, agenttype, annexdocids, requestLogId, operatorDept, signdocids, signworkflowids, isMobile, HandWrittenSign, SpeechAttachment, receivedpersonids, remarklocation, isSubmitDirect);
            if (upsuccess) {
                writeLog("新请求=" + requestid + ",更新 workflow_requestlog 成功,old主键=" + logid);
            } else {
                writeLog("更新 workflow_requestlog 失败，oldrequestid=" + reqid + ",newrequestid="+requestid+",程序执行回滚>>>");
                delData(requestid);
                writeLog("回滚完成，程序退出<<<");
                break;
            }
        }

        // workflow_nownode
        rsds.execute("select * from workflow_nownode where requestid=" + reqid);
        while (rsds.next()) {
            //requestid,nownodeid,nownodetype,nownodeattribute

            String nownodeid = Util.null2String(rsds.getString("nownodeid"));
            String nownodetype = Util.null2String(rsds.getString("nownodetype"));
            String nownodeattribute = Util.null2String(rsds.getString("nownodeattribute"));
            String insql = "insert into workflow_nownode(requestid,nownodeid,nownodetype,nownodeattribute) values(?,?,?,?)";
            upsuccess = rs.executeUpdate(insql, requestid, nownodeid, nownodetype, nownodeattribute);
            if (upsuccess) {
                writeLog("新请求=" + requestid + ",更新 workflow_nownode 成功");
            } else {
                writeLog("更新 workflow_nownode 失败，oldrequestid=" + reqid + ",newrequestid="+requestid+",程序执行回滚>>>");
                delData(requestid);
                writeLog("回滚完成，程序退出<<<");
                break;
            }
        }


        // workflow_track
        rsds.execute("select * from workflow_track where requestid=" + reqid);
        while (rsds.next()) {
            String id = rsds.getString("id");
            String optKind = Util.null2String(rsds.getString("optKind"));
            String nodeId = Util.null2String(rsds.getString("nodeId"));
            String isbill = Util.null2String(rsds.getString("isBill"));
            String fieldLableId = Util.null2String(rsds.getString("fieldLableId"));
            String fieldId = Util.null2String(rsds.getString("fieldId"));
            String fieldHtmlType = Util.null2String(rsds.getString("fieldHtmlType"));
            String fieldType = Util.null2String(rsds.getString("fieldType"));
            String fieldNameCn = Util.null2String(rsds.getString("fieldNameCn"));
            String fieldNameEn = Util.null2String(rsds.getString("fieldNameEn"));
            String fieldOldText = Util.null2String(rsds.getString("fieldOldText"));
            String fieldNewText = Util.null2String(rsds.getString("fieldNewText"));
            String modifierType = Util.null2String(rsds.getString("modifierType"));
            String agentId = Util.null2String(rsds.getString("agentId"));
            String modifierId = Util.null2String(rsds.getString("modifierId"));
            String modifierIP = Util.null2String(rsds.getString("modifierIP"));
            String modifyTime = Util.null2String(rsds.getString("modifyTime"));
            String fieldNameTw = Util.null2String(rsds.getString("fieldNameTw"));
            String insql = "insert into workflow_track(optKind,requestId,nodeId,isBill,fieldLableId,fieldId,fieldHtmlType,fieldType,fieldNameCn,fieldNameEn,fieldOldText,fieldNewText,modifierType,agentId,modifierId,modifierIP,modifyTime,fieldNameTw) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            upsuccess = rs.executeUpdate(insql, optKind, requestid, nodeId, isbill, fieldLableId, fieldId, fieldHtmlType, fieldType, fieldNameCn, fieldNameEn, fieldOldText, fieldNewText, modifierType, agentId, modifierId, modifierIP, modifyTime, fieldNameTw);
            if (upsuccess) {
                writeLog("新请求=" + requestid + ",更新 workflow_track 成功,old主键=" + id);
            } else {
                writeLog("更新 workflow_track 失败，oldrequestid=" + reqid + ",newrequestid="+requestid+",程序执行回滚>>>");
                delData(requestid);
                delTableData(requestid, "");
                writeLog("回滚完成，程序退出<<<");
                break;
            }
        }

        // workflow_trackdetail
        rsds.execute("select * from workflow_trackdetail where requestid=" + reqid);
        while (rsds.next()) {
            String id = rsds.getString("id");
            String sn = Util.null2String(rsds.getString("sn"));
            String optKind = Util.null2String(rsds.getString("optKind"));
            String optType = Util.null2String(rsds.getString("optType"));
            String requestId = Util.null2String(rsds.getString("requestId"));
            String nodeId = Util.null2String(rsds.getString("nodeId"));
            String isbill = Util.null2String(rsds.getString("isBill"));
            String fieldLableId = Util.null2String(rsds.getString("fieldLableId"));
            String fieldGroupId = Util.null2String(rsds.getString("fieldGroupId"));
            String fieldId = Util.null2String(rsds.getString("fieldId"));
            String fieldHtmlType = Util.null2String(rsds.getString("fieldHtmlType"));
            String fieldType = Util.null2String(rsds.getString("fieldType"));
            String fieldNameCn = Util.null2String(rsds.getString("fieldNameCn"));
            String fieldNameEn = Util.null2String(rsds.getString("fieldNameEn"));
            String fieldOldText = Util.null2String(rsds.getString("fieldOldText"));
            String fieldNewText = Util.null2String(rsds.getString("fieldNewText"));
            String modifierType = Util.null2String(rsds.getString("modifierType"));
            String agentId = Util.null2String(rsds.getString("agentId"));
            String modifierId = Util.null2String(rsds.getString("modifierId"));
            String modifierIP = Util.null2String(rsds.getString("modifierIP"));
            String modifyTime = Util.null2String(rsds.getString("modifyTime"));
            String fieldNameTw = Util.null2String(rsds.getString("fieldNameTw"));

            String insql = "insert into workflow_trackdetail(sn,optKind,optType,requestId,nodeId,isBill,fieldLableId,fieldGroupId,fieldId,fieldHtmlType,fieldType,fieldNameCn,fieldNameEn,fieldOldText,fieldNewText,modifierType,agentId,modifierId,modifierIP,modifyTime,fieldNameTw) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            upsuccess = rs.executeUpdate(insql, sn, optKind, optType, requestId, nodeId, isbill, fieldLableId, fieldGroupId, fieldId, fieldHtmlType, fieldType, fieldNameCn, fieldNameEn, fieldOldText, fieldNewText, modifierType, agentId, modifierId, modifierIP, modifyTime, fieldNameTw);
            if (upsuccess) {
                writeLog("新请求=" + requestid + ",更新 workflow_trackdetail 成功,old主键=" + id);
            } else {
                writeLog("更新 workflow_trackdetail 失败，oldrequestid=" + reqid + ",newrequestid="+requestid+",程序执行回滚>>>");
                delData(requestid);
                delTableData(requestid, "");
                writeLog("回滚完成，程序退出<<<");
                break;
            }
        }

        // workflow_penetrateLog
        rsds.execute("select * from workflow_penetrateLog where requestid=" + reqid);
        while (rsds.next()) {
            String id = rsds.getString("id");
            String nodeid = Util.null2String(rsds.getString("nodeid"));
            String logtype = Util.null2String(rsds.getString("logtype"));
            String operatedate = Util.null2String(rsds.getString("operatedate"));
            String operatetime = Util.null2String(rsds.getString("operatetime"));
            String operator = Util.null2String(rsds.getString("operator"));
            String remark = Util.null2String(rsds.getString("remark"));
            String clientip = Util.null2String(rsds.getString("clientip"));
            String operatortype = Util.null2String(rsds.getString("operatortype"));
            String destnodeid = Util.null2String(rsds.getString("destnodeid"));
            String receivedPersons = Util.null2String(rsds.getString("receivedPersons"));
            String showorder = Util.null2String(rsds.getString("showorder"));
            String agentorbyagentid = Util.null2String(rsds.getString("agentorbyagentid"));
            String agenttype = Util.null2String(rsds.getString("agenttype"));
            String LOGID = Util.null2String(rsds.getString("LOGID"));
            String annexdocids = Util.null2String(rsds.getString("annexdocids"));
            String requestLogId = Util.null2String(rsds.getString("requestLogId"));
            String operatorDept = Util.null2String(rsds.getString("operatorDept"));
            String signdocids = Util.null2String(rsds.getString("signdocids"));
            String signworkflowids = Util.null2String(rsds.getString("signworkflowids"));
            String isMobile = Util.null2String(rsds.getString("isMobile"));
            String HandWrittenSign = Util.null2String(rsds.getString("HandWrittenSign"));
            String SpeechAttachment = Util.null2String(rsds.getString("SpeechAttachment"));
            String remarklocation = Util.null2String(rsds.getString("remarklocation"));

            String insql = "insert into workflow_penetrateLog(requestid,workflowid,nodeid,logtype,operatedate,operatetime,operator,remark,clientip,operatortype,destnodeid,receivedPersons,showorder,agentorbyagentid,agenttype,LOGID,annexdocids,requestLogId,operatorDept,signdocids,signworkflowids,isMobile,HandWrittenSign,SpeechAttachment,remarklocation) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            upsuccess = rs.executeUpdate(insql, requestid, workflowid, nodeid, logtype, operatedate, operatetime, operator, remark, clientip, operatortype, destnodeid, receivedPersons, showorder, agentorbyagentid, agenttype, LOGID, annexdocids, requestLogId, operatorDept, signdocids, signworkflowids, isMobile, HandWrittenSign, SpeechAttachment, remarklocation);
            if (upsuccess) {
                writeLog("新请求=" + requestid + ",更新 workflow_penetrateLog 成功,old主键=" + id);
            } else {
                writeLog("更新 workflow_penetrateLog 失败，oldrequestid=" + reqid + ",newrequestid="+requestid+",程序执行回滚>>>");
                delData(requestid);
                delTableData(requestid, "");
                writeLog("回滚完成，程序退出<<<");
                break;
            }
        }

        // workflow_agentpersons
        rsds.execute("select * from workflow_agentpersons where requestid=" + reqid);
        while (rsds.next()) {

            String receivedPersons = Util.null2String(rsds.getString("receivedPersons"));
            String coadjutants = Util.null2String(rsds.getString("coadjutants"));
            String groupdetailid = Util.null2String(rsds.getString("groupdetailid"));

            String insql = "insert into workflow_agentpersons(requestid,receivedPersons,coadjutants,groupdetailid) values(?,?,?,?)";
            upsuccess = rs.executeUpdate(insql, requestid, receivedPersons, coadjutants, groupdetailid);
            if (upsuccess) {
                writeLog("新请求=" + requestid + ",更新 workflow_agentpersons 成功");
            } else {
                writeLog("更新 workflow_agentpersons 失败，oldrequestid=" + reqid + ",newrequestid="+requestid+",程序执行回滚>>>");
                delData(requestid);
                delTableData(requestid, "");
                writeLog("回滚完成，程序退出<<<");
                break;
            }
        }

        // workflow_approvelog
        rsds.execute("select * from workflow_approvelog where requestid=" + reqid);
        while (rsds.next()) {

            String nodeid = Util.null2String(rsds.getString("nodeid"));
            String remark = Util.null2String(rsds.getString("remark"));
            String operator = Util.null2String(rsds.getString("operator"));
            String logdate = Util.null2String(rsds.getString("logdate"));
            String logtime = Util.null2String(rsds.getString("logtime"));

            String insql = "insert into workflow_approvelog (requestid,nodeid,remark,operator,logdate,logtime) values(?,?,?,?,?,?)";
            upsuccess = rs.executeUpdate(insql, requestid, nodeid, remark, operator, logdate, logtime);
            if (upsuccess) {
                writeLog("新请求=" + requestid + ",更新 workflow_approvelog 成功");
            } else {
                writeLog("更新 workflow_approvelog 失败，oldrequestid=" + reqid + ",newrequestid="+requestid+",程序执行回滚>>>");
                delData(requestid);
                delTableData(requestid, "");
                writeLog("回滚完成，程序退出<<<");
                break;
            }
        }

        // workflow_requestoperatelog
        rsds.execute("select * from workflow_requestoperatelog where requestid=" + reqid);
        while (rsds.next()) {
            String id = Util.null2String(rsds.getString("id"));
            String nodeid = Util.null2String(rsds.getString("nodeid"));
            String isremark = Util.null2String(rsds.getString("isremark"));
            String operatorid = Util.null2String(rsds.getString("operatorid"));
            String operatortype = Util.null2String(rsds.getString("operatortype"));
            String operatedate = Util.null2String(rsds.getString("operatedate"));
            String operatetime = Util.null2String(rsds.getString("operatetime"));
            String operatetype = Util.null2String(rsds.getString("operatetype"));
            String operatename = Util.null2String(rsds.getString("operatename"));
            String operatecode = Util.null2String(rsds.getString("operatecode"));
            String isinvalid = Util.null2String(rsds.getString("isinvalid"));
            String invalidid = Util.null2String(rsds.getString("invalidid"));
            String invaliddate = Util.null2String(rsds.getString("invaliddate"));
            String invalidtime = Util.null2String(rsds.getString("invalidtime"));
            String insql = "insert into workflow_requestoperatelog (requestid,nodeid,isremark,operatorid,operatortype,operatedate,operatetime,operatetype,operatename,operatecode,isinvalid,invalidid,invaliddate,invalidtime) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            upsuccess = rs.executeUpdate(insql, requestid,nodeid,isremark,operatorid,operatortype,operatedate,operatetime,operatetype,operatename,operatecode,isinvalid,invalidid,invaliddate,invalidtime);
            if (upsuccess) {
                writeLog("新请求=" + requestid + ",更新 workflow_requestoperatelog 成功");
            } else {
                writeLog("更新 workflow_requestoperatelog 失败，oldrequestid=" + reqid + ",newrequestid="+requestid+",主键="+id+",程序执行回滚>>>");
                delData(requestid);
                delTableData(requestid, "");
                writeLog("回滚完成，程序退出<<<");
                upsuccess = false;  // 明细表判断
                break;
            }
        }
        if(upsuccess) {
            // workflow_requestoperatelog
            rsds.execute("select * from workflow_requestoperatelog_dtl where requestid=" + reqid);
            while (rsds.next()) {
                String entitytype = Util.null2String(rsds.getString("entitytype"));
                String entityid = Util.null2String(rsds.getString("entityid"));
                String ismodify = Util.null2String(rsds.getString("ismodify"));
                String fieldname = Util.null2String(rsds.getString("fieldname"));
                String ovalue = Util.null2String(rsds.getString("ovalue"));
                String nvalue = Util.null2String(rsds.getString("nvalue"));
                // 特殊情况，optlogid 为主表的id
                rs.executeQuery("select id from workflow_requestoperatelog where requestid=?", requestid);
                rs.next();
                String optlogid = Util.null2String(rs.getString("id"));
                String insql = "insert into workflow_requestoperatelog_dtl (requestid,optlogid,entitytype,entityid,ismodify,fieldname,ovalue,nvalue) values(?,?,?,?,?,?,?,?)";
                upsuccess = rs.executeUpdate(insql, requestid,optlogid,entitytype,entityid,ismodify,fieldname,ovalue,nvalue);
                if (upsuccess) {
                    writeLog("新请求=" + requestid + ",更新 workflow_requestoperatelog_dtl 成功");
                } else {
                    writeLog("更新 workflow_requestoperatelog_dtl 失败，oldrequestid=" + reqid + ",newrequestid=" + requestid + ",主键=" + optlogid + ",程序执行回滚>>>");
                    delData(requestid);
                    delTableData(requestid, "");
                    rs.executeUpdate("update workflow_requestoperatelog_dtl set optlogid=? where optlogid=? and requestid=?",("-" +optlogid),optlogid,requestid);
                    writeLog("回滚完成，程序退出<<<");
                    break;
                }
            }
        }

        // Workflow_SharedScope
        rsds.execute("select * from Workflow_SharedScope where requestid=" + reqid);
        while (rsds.next()) {
            String wfid = Util.null2String(rsds.getString("wfid"));
            String permissiontype = Util.null2String(rsds.getString("permissiontype"));
            String seclevel = Util.null2String(rsds.getString("seclevel"));
            String departmentid = Util.null2String(rsds.getString("departmentid"));
            String deptlevel = Util.null2String(rsds.getString("deptlevel"));
            String subcompanyid = Util.null2String(rsds.getString("subcompanyid"));
            String sublevel = Util.null2String(rsds.getString("sublevel"));
            String userid = Util.null2String(rsds.getString("userid"));
            String describ = Util.null2String(rsds.getString("describ"));
            String seclevelMax = Util.null2String(rsds.getString("seclevelMax"));
            String deptlevelMax = Util.null2String(rsds.getString("deptlevelMax"));
            String sublevelMax = Util.null2String(rsds.getString("sublevelMax"));
            String roleid = Util.null2String(rsds.getString("roleid"));
            String rolelevel = Util.null2String(rsds.getString("rolelevel"));
            String roleseclevel = Util.null2String(rsds.getString("roleseclevel"));
            String roleseclevelMax = Util.null2String(rsds.getString("roleseclevelMax"));
            String iscanread = Util.null2String(rsds.getString("iscanread"));
            String operator = Util.null2String(rsds.getString("operator"));
            String currentnodeid = Util.null2String(rsds.getString("currentnodeid"));
            String joblevel = Util.null2String(rsds.getString("joblevel"));
            String jobid = Util.null2String(rsds.getString("jobid"));
            String jobobj = Util.null2String(rsds.getString("jobobj"));
            String currentid = Util.null2String(rsds.getString("currentid"));
            String jobobjid = Util.null2String(rsds.getString("jobobjid"));
            String id = Util.null2String(rsds.getString("id"));

            String insql = "insert into Workflow_SharedScope (wfid,requestid,permissiontype,seclevel,departmentid,deptlevel,subcompanyid,sublevel,userid,describ,seclevelMax,deptlevelMax,sublevelMax,roleid,rolelevel,roleseclevel,roleseclevelMax,iscanread,operator,currentnodeid,joblevel,jobid,jobobj,currentid,jobobjid) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            upsuccess = rs.executeUpdate(insql, wfid,requestid,permissiontype,seclevel,departmentid,deptlevel,subcompanyid,sublevel,userid,describ,seclevelMax,deptlevelMax,sublevelMax,roleid,rolelevel,roleseclevel,roleseclevelMax,iscanread,operator,currentnodeid,joblevel,jobid,jobobj,currentid,jobobjid);
            if (upsuccess) {
                writeLog("新请求=" + requestid + ",更新 Workflow_SharedScope 成功");
            } else {
                writeLog("更新 Workflow_SharedScope 失败，oldrequestid=" + reqid + ",newrequestid="+requestid+",主键="+id+",程序执行回滚>>>");
                delData(requestid);
                delTableData(requestid, "");
                writeLog("回滚完成，程序退出<<<");
                break;
            }

        }


    }

    private void delTableData(int requestid, String tablename) {
        if (tablename.isEmpty() == false) {
            RecordSet rs = new RecordSet();
            String dsql = "update " + tablename + " set requestid=? where requestid=?";
            writeLog("回滚>>" + dsql);
            rs.executeUpdate(dsql, requestid * -1, requestid);

        }

    }

    private void delData(int requestid) {

        RecordSet rs = new RecordSet();
        String dsql = "update workflow_currentoperator set requestid=? where requestid=?";
        writeLog("回滚>>" + dsql);
        rs.executeUpdate(dsql, requestid * -1, requestid);

        dsql = dsql.replace("currentoperator", "requestbase");
        writeLog("回滚>>" + dsql);
        rs.executeUpdate(dsql, requestid * -1, requestid);

        dsql = dsql.replace("requestbase", "requestlog");
        writeLog("回滚>>" + dsql);
        rs.executeUpdate(dsql, requestid * -1, requestid);

        dsql = dsql.replace("requestlog", "form");
        writeLog("回滚>>" + dsql);
        rs.executeUpdate(dsql, requestid * -1, requestid);

        dsql = dsql.replace("form", "nownode");
        writeLog("回滚>>" + dsql);
        rs.executeUpdate(dsql, requestid * -1, requestid);

        dsql = dsql.replace("nownode", "track");
        writeLog("回滚>>" + dsql);
        rs.executeUpdate(dsql, requestid * -1, requestid);

        dsql = dsql.replace("track", "trackdetail");
        writeLog("回滚>>" + dsql);
        rs.executeUpdate(dsql, requestid * -1, requestid);

        dsql = dsql.replace("trackdetail", "agentpersons");
        writeLog("回滚>>" + dsql);
        rs.executeUpdate(dsql, requestid * -1, requestid);

        dsql = dsql.replace("agentpersons", "approvelog");
        writeLog("回滚>>" + dsql);
        rs.executeUpdate(dsql, requestid * -1, requestid);

        dsql = dsql.replace("approvelog", "requestoperatelog");
        writeLog("回滚>>" + dsql);
        rs.executeUpdate(dsql, requestid * -1, requestid);

        dsql = dsql.replace("requestoperatelog", "SharedScope");
        writeLog("回滚>>" + dsql);
        rs.executeUpdate(dsql, requestid * -1, requestid);





    }


}
