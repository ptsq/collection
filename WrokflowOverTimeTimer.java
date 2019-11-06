package weaver.system;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import weaver.WorkPlan.CreateWorkplanByWorkflow;
import weaver.conn.RecordSet;
import weaver.conn.RecordSetTrans;
import weaver.cpt.util.CptWfUtil;
import weaver.crm.Maint.CustomerInfoComInfo;
import weaver.general.BaseBean;
import weaver.general.SendMail;
import weaver.general.StaticObj;
import weaver.general.TimeUtil;
import weaver.general.Util;
import weaver.hrm.User;
import weaver.hrm.company.DepartmentComInfo;
import weaver.hrm.resource.ResourceComInfo;
import weaver.interfaces.workflow.action.Action;
import weaver.sms.SMSManager;
import weaver.sms.SmsCache;
import weaver.soa.workflow.WorkFlowInit;
import weaver.soa.workflow.bill.BillBgOperation;
import weaver.soa.workflow.request.RequestService;
import weaver.systeminfo.SystemEnv;
import weaver.wechat.SaveAndSendWechat;
import weaver.wechat.util.WechatPropConfig;
import weaver.workflow.mode.FieldInfo;
import weaver.workflow.msg.PoppupRemindInfoUtil;
import weaver.workflow.request.*;
import weaver.workflow.request.WFAutoApproveUtils.AutoApproveParams;
import weaver.workflow.webservices.WorkflowBaseInfo;
import weaver.workflow.webservices.WorkflowDetailTableInfo;
import weaver.workflow.webservices.WorkflowMainTableInfo;
import weaver.workflow.webservices.WorkflowRequestInfo;
import weaver.workflow.webservices.WorkflowRequestTableField;
import weaver.workflow.webservices.WorkflowRequestTableRecord;
import weaver.workflow.webservices.WorkflowService;
import weaver.workflow.webservices.WorkflowServiceImpl;
import weaver.workflow.workflow.WFSubDataAggregation;
import weaver.workflow.workflow.WorkflowComInfo;
import weaver.worktask.request.RequestCreateByWF;

/**
 * 工作流超时检查





 * User: mackjoe
 * Date: 2006-4-17
 * Time: 17:19:27
 */
public class WrokflowOverTimeTimer extends BaseBean implements ThreadWork{
    private RecordSet rs;
    private RecordSet rs1;
    private RecordSet rs2;
    private RecordSet rs3;
    private RecordSet rs4;
    private RecordSet rs5;
    private RecordSet rs8;
    private PoppupRemindInfoUtil poppupRemindInfoUtil;
    private ResourceComInfo resource;
    private CustomerInfoComInfo crminfo;
	private OverTimeSetBean overTimeBean;
    private ArrayList operatorsWfEnd;
    private Log log;
    private User user;
    private ArrayList wfremindusers;
    private ArrayList wfusertypes;
    private ArrayList nextnodeids;
    private ArrayList nextnodetypes;
    private ArrayList nextlinkids;
    private ArrayList nextlinknames;
    private ArrayList operatorshts;
    private ArrayList nextnodeattrs;
    private ArrayList nextnodepassnums;
    private ArrayList linkismustpasss;
    private String innodeids="";
    private RequestComInfo requestcominfo;
    private SaveAndSendWechat saveAndSendWechat;//微信提醒(QC:98106)
	private ArrayList operator89List = new ArrayList();//add by liaodong for qc80034  start
    public WrokflowOverTimeTimer() {
        rs=new RecordSet();
        rs1=new RecordSet();
        rs2=new RecordSet();
        rs3=new RecordSet();
        rs4=new RecordSet();
        rs5=new RecordSet();
        rs8=new RecordSet();
        operatorsWfEnd = new ArrayList();
        wfremindusers = new ArrayList();
        wfusertypes = new ArrayList();
        log= LogFactory.getLog("WrokflowOverTimeTimer");
        poppupRemindInfoUtil = new PoppupRemindInfoUtil();//xwj for td3450 20060111
        try{
            resource=new ResourceComInfo();
            crminfo=new CustomerInfoComInfo();
			overTimeBean=new OverTimeSetBean();
			saveAndSendWechat=new SaveAndSendWechat();//微信提醒(QC:98106)
            user=new User();
            requestcominfo = new RequestComInfo();
        }catch(Exception e){
             e.printStackTrace();
        }
    }

    /**
     * 超时处理
     */
    public void doThreadWork() {
        RecordSet rs = new RecordSet();
		boolean logBug = false;
        String wfovertimeDebug =Util.null2String(new weaver.general.BaseBean().getPropValue("workflowovertimeDebug","WORKFLOWOVERTIMEDEBUG"));
		if("1".equals(wfovertimeDebug)) logBug = true;
		if(logBug) writeLog("workflowOvertime====start");
      //获取系统短信签名,是否是长短信,分割字数
        String sign=Util.null2String(SmsCache.getSmsSet().getSign());
        String signPos=SmsCache.getSmsSet().getSignPos();
        HashMap overTimeMap = getNodeLinkOverTimeInfo(); // 获取所有出口的超时提醒信息
        
            //获得数据库服务器当前时间
            String nowdatetime = TimeUtil.getCurrentTimeString();
            String sql="";
            if (rs5.getDBType().equals("oracle")) {
                sql = "select to_char(sysdate,'yyyy-mm-dd hh24:mi:ss') nowdatetime from dual";
            } else {
                sql = "select convert(char(10),getdate(),20)+' '+convert(char(8),getdate(),108) nowdatetime";
            }
            rs5.executeSql(sql);
            if (rs5.next()) {
                nowdatetime = rs5.getString("nowdatetime");
            }
            sql="select distinct requestid,nodeid,workflowid,workflowtype from workflow_currentoperator where workflowid<>1 and isremark='0' and " +
                    "(EXISTS (select 1 from workflow_nodelink t1 where t1.wfrequestid is null and EXISTS (select 1 from workflow_base t2 where t1.workflowid=t2.id and t2.isvalid = '1' and (t2.istemplate is null or t2.istemplate<>'1')) and (t1.nodepasshour>0 or t1.nodepassminute>0 or (t1.dateField is not null and t1.dateField != ' ')) and workflow_currentoperator.nodeid=t1.nodeid) or " +//路径设置的超时节点
                    "EXISTS (select 1 from workflow_nodelink t1 where EXISTS (select 1 from workflow_base t2 where t1.workflowid=t2.id  and t2.isvalid = '1' and (t2.istemplate is null or t2.istemplate<>'1')) and (t1.nodepasshour>0 or t1.nodepassminute>0 or (t1.dateField is not null and t1.dateField != ' ')) and workflow_currentoperator.nodeid=t1.nodeid and workflow_currentoperator.requestid=t1.wfrequestid)) "+    //前台界面设置的超时
                    "and ((not(isreminded is not null and lastRemindDatetime is null)) or isprocessed is null or (not(isreminded_csh is not null and lastRemindDatetime is null))) group by requestid,nodeid,workflowid,workflowtype order by requestid asc ,nodeid";
            rs.executeSql(sql);
            while(rs.next()){
                int requestid=rs.getInt("requestid");
				if(logBug) writeLog("requestid===="+requestid);
                int nodeid=Util.getIntValue(rs.getString("nodeid"));
                int workflowid=Util.getIntValue(rs.getString("workflowid"));
                int workflowtype=Util.getIntValue(rs.getString("workflowtype"));
                ArrayList userlist=new ArrayList();
                ArrayList usertypelist=new ArrayList();
                ArrayList agenttypelist=new ArrayList();
                ArrayList agentorbyagentidlist=new ArrayList();
                //ArrayList isremindedlist=new ArrayList();
                //ArrayList isreminded_cshlist=new ArrayList();
                ArrayList lastRemindDatetimeList=new ArrayList();
                ArrayList isprocessedlist=new ArrayList();
                ArrayList currentdatetimelist=new ArrayList();
                ArrayList idlist=new ArrayList();
                boolean isCanSubmit = true;
                sql="select * from workflow_currentoperator where workflowid<>1 and isremark='0' and ((not(isreminded is not null and lastRemindDatetime is null)) or isprocessed is null or (not(isreminded_csh is not null and lastRemindDatetime is null))) and requestid="+requestid+" and nodeid="+nodeid+" order by requestid desc,id";
				rs5.executeSql(sql);
                while(rs5.next()){
                    String currentdatetimes=rs5.getString("receivedate")+" "+rs5.getString("receivetime");
                    String userids=rs5.getString("userid");
                    String usertypes=rs5.getString("usertype");
                    String agenttypes=rs5.getString("agenttype");
                    String agentorbyagentids=rs5.getString("agentorbyagentid");
                    //String isremindeds=rs5.getString("isreminded");
                    //String isreminded_cshs=rs5.getString("isreminded_csh");//超时后提醒
                    String lastRemindDatetime = Util.null2String(rs5.getString("lastRemindDatetime")); // 上次提醒时间
                    String isprocesseds=rs5.getString("isprocessed");
                    String ids=rs5.getString("id");
                    
                    WFForwardManager wfforwardMgr = new WFForwardManager();
                    wfforwardMgr.setWorkflowid(workflowid);
                    wfforwardMgr.setNodeid(nodeid);
                    wfforwardMgr.setIsremark("0");
                    wfforwardMgr.setRequestid(requestid);
                    wfforwardMgr.setBeForwardid(Util.getIntValue(ids));
                    wfforwardMgr.getWFNodeInfo();
                    
                    if (!wfforwardMgr.getCanSubmit()) {
                        isCanSubmit = false;
                        break;
                    }
                    
                    userlist.add(userids);
                    usertypelist.add(usertypes);
                    agenttypelist.add(agenttypes);
                    agentorbyagentidlist.add(agentorbyagentids);
                    //isremindedlist.add(isremindeds);
                    //isreminded_cshlist.add(isreminded_cshs);
                    lastRemindDatetimeList.add(lastRemindDatetime);
                    isprocessedlist.add(isprocesseds);
                    currentdatetimelist.add(currentdatetimes);
                    idlist.add(ids);
                }
                
                if (!isCanSubmit) continue;
                
                //会签特殊处理,为会签时,只取第一个用户来进行节点流转检查,并获得超时设置信息(因为其它用户获得的信息一样,不重复获取了)
                if(userlist.size()>0){
                int userid=Util.getIntValue((String)userlist.get(0));
                int usertype=Util.getIntValue((String)usertypelist.get(0));
                //int isreminded=Util.getIntValue((String)isremindedlist.get(0),0);
                //int isreminded_csh=Util.getIntValue((String)isreminded_cshlist.get(0),0);
                HashMap<Integer, String> lastRemindDatetimeMap = this.getLastRemindDatetimeInfo((String) lastRemindDatetimeList.get(0));
                int isprocessed=Util.getIntValue((String)isprocessedlist.get(0),0);
                String currentdatetime=(String)currentdatetimelist.get(0);
                int nextlinkid=getNextNode(requestid,nodeid,userid,usertype);
				if(logBug) writeLog("nextlinkid===="+nextlinkid);
                int language=7;
                user.setUid(userid);
                user.setLogintype((usertype+1)+"");
				user.setLastname(resource.getLastname(userid+""));
                sql="select * from HrmResource where id="+userid;
                rs1.executeSql(sql);
                if(rs1.next()){
                    language=Util.getIntValue(rs1.getString("systemlanguage"),7);
                    user.setLanguage(language);
                }
                sql="select nodeid,isremind,nodepasshour,nodepassminute,remindhour,remindminute,FlowRemind,MsgRemind,MailRemind,ChatsRemind,ProcessorOpinion,"+//微信提醒(QC:98106)
                        "isnodeoperator,iscreater,ismanager,isother,remindobjectids,isautoflow,flownextoperator,flowobjectids,destnodeid"+
                        ",dateField,timeField"+
                        ",CustomWorkflowid"+
                        ",flowobjectreject,flowobjectsubmit"+
                        ",selectnodepass " +
                        ",InfoCentreRemind,InfoCentreRemind_csh,CustomWorkflowid_csh " +
                        " from workflow_nodelink where id="+nextlinkid ;
                //System.out.println(requestid+"|"+nodeid+"sql:"+sql);
                sql = "select * from workflow_nodelink where id="+nextlinkid ;
                //log.debug(nodeid+"sql:"+sql);
                rs1.executeSql(sql);
                if(rs1.next()){
                    int nodepasshour=Util.getIntValue(rs1.getString("nodepasshour"),0);
                    int nodepassminute=Util.getIntValue(rs1.getString("nodepassminute"),0);
                    
                    String dateField = Util.null2String(rs1.getString("dateField"));
                    String timeField = Util.null2String(rs1.getString("timeField"));
                    
                    int isautoflow=Util.getIntValue(rs1.getString("isautoflow"),0);
                    int flownextoperator=Util.getIntValue(rs1.getString("flownextoperator"),0);
                    int nodeid_tmp = Util.getIntValue(rs1.getString("nodeid"),0);
                    String flowobjectids=Util.null2String(rs1.getString("flowobjectids"));
                    int destnodeid=Util.getIntValue(rs1.getString("destnodeid"));
                    String ProcessorOpinion=Util.null2String(rs1.getString("ProcessorOpinion"));
                    
                    String flowobjectreject=Util.null2String(rs1.getString("flowobjectreject"));//流程退回
                    String flowobjectsubmit=Util.null2String(rs1.getString("flowobjectsubmit"));//流程提交
                    
                    
                    int selectnodepass=Util.getIntValue(rs1.getString("selectnodepass"),0); //节点超时类型 
                    
                    
                    sql="select nodepasshour,nodepassminute,ProcessorOpinion,dateField,timeField from workflow_NodeLink where wfrequestid="+requestid+" and destnodeid="+destnodeid+" and nodeid="+nodeid_tmp;
                    //System.out.println(sql);
                    rs2.executeSql(sql);
                    if(rs2.next()){
                        nodepasshour=Util.getIntValue(rs2.getString("nodepasshour"),0);
                        nodepassminute=Util.getIntValue(rs2.getString("nodepassminute"),0);
                        //ProcessorOpinion=Util.null2String(rs2.getString("ProcessorOpinion"));
                        dateField = Util.null2String(rs1.getString("dateField"));
                        timeField = Util.null2String(rs1.getString("timeField"));
                        if(isautoflow==0){
                            isautoflow=1;
                            flownextoperator=1;
                        }
                    }
                    
                    String dateValue="";
                    if(!"".equals(dateField) && selectnodepass == 2){
                    	dateValue = getDateValue(requestid, dateField, timeField);
                    }
                    boolean dateProcess = false;
                    long timedifference =0;
                    if(!"".equals(dateValue)){
                    	
                    	try {
                    		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    		java.util.Date d1 = sdf.parse(nowdatetime);
                    		java.util.Date d2 = sdf.parse(dateValue);
							java.util.Date d3 = sdf.parse(currentdatetime);
                    		dateProcess = d1.after(d2);
                    		timedifference = d2.getTime() - d3.getTime();
                    		if(timedifference<0)timedifference=0;
                    		else timedifference = timedifference/1000;
                    		
						} catch (Exception e) {
							e.printStackTrace();
						}
                    }
                    
					if(ProcessorOpinion.trim().equals("")) ProcessorOpinion=SystemEnv.getHtmlLabelName(22263,language);
                    long processsecond=nodepasshour*3600+nodepassminute*60;
                    
                    if(selectnodepass==2 && !"".equals(dateValue)){
                    	//processsecond = timedifference;
						processsecond = overTimeBean.getOverTime(currentdatetime,dateValue);
                    }

                    long nowSecond = overTimeBean.getOverTime(currentdatetime,nowdatetime); // 接收时间至当前时间的秒数
                    ArrayList overTimeList = overTimeMap.get(nextlinkid) == null ? new ArrayList() : (ArrayList) overTimeMap.get(nextlinkid); // 当前出口的所有超时提醒信息
                    int listIndex = 0;
                    /***********************************************************************************************/
                    //超时提醒--begin
                    for(listIndex = 0; listIndex < overTimeList.size(); listIndex++) {
                    	HashMap map = (HashMap) overTimeList.get(listIndex);
                    	int remindtype = (Integer) map.get("remindtype");
                    	if(remindtype != 0) { // 只提醒超时前提醒
                    		break;
                    	}
                    	
                    	int id = (Integer) map.get("id");
                    	if(id <= 0) {
                    		continue;
                    	}
                    	//String lastRemindDatetime = lastRemindDatetimeMap.get(id) == null ? "" : lastRemindDatetimeMap.get(id);
						String lastRemindDatetime = lastRemindDatetimeMap.get(id) == null ? "2000-01-01 00:00:00" : lastRemindDatetimeMap.get(id);
                    	long lastRemindSecond = overTimeBean.getOverTime(currentdatetime,lastRemindDatetime); // 接收时间至上次提醒时间之间的秒数
                    	if(lastRemindSecond < 0) {
                    		lastRemindSecond = 0;
                    	}
                    	boolean lastDateProcess = false;
    					if(!"".equals(dateValue) && !"".equals(lastRemindDatetime)) {
                        	try {
                        		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        		java.util.Date d1 = sdf.parse(lastRemindDatetime);
                        		java.util.Date d2 = sdf.parse(dateValue);
                        		lastDateProcess = d1.after(d2);
    						} catch (Exception e) {
    							e.printStackTrace();
    						}
                        }
                    	
                    	int remindhour = (Integer) map.get("remindhour");
                    	int remindminute = (Integer) map.get("remindminute");
                    	
                    	long remindsecond=0;
						if(selectnodepass == 2) {
                    		remindsecond = processsecond - (remindhour * 3600 + remindminute * 60);
                    	}else {
							if(nodepasshour>=remindhour){
								if(nodepassminute>=remindminute){
									remindsecond=(nodepasshour-remindhour)*3600+(nodepassminute-remindminute)*60;
								}else{
									remindsecond=(nodepasshour-remindhour-1)*3600+(60+nodepassminute-remindminute)*60;
								}
							}
						}
						//System.out.println("超时processsecond："+processsecond+"====remindsecond:"+remindsecond+"===dateProcess:"+dateProcess+"===requestid:"+requestid);
						
						int repeatremind = (Integer) map.get("repeatremind"); // 是否重复提醒。1-是，0-不是
						boolean isremind = false; // 是否提醒
						boolean hasLastRemind = false; // 此提醒设置之前是否提醒过
						if(logBug){ 
							writeLog("nowSecond===="+nowSecond);
							writeLog("remindsecond===="+remindsecond);
							writeLog("dateProcess===="+dateProcess);
						}
						if(nowSecond >= remindsecond || dateProcess) { // 判断本次是否提醒
							isremind = true;
						}
						if(isremind && !"".equals(lastRemindDatetime)) { // 判断上一次超时计划任务执行时是否已超时，已超时则应该提醒过
							if(lastRemindSecond >= remindsecond || lastDateProcess) { // 提醒过则不提醒
								isremind = false;
								hasLastRemind = true;
							}
							
							if(!isremind && repeatremind == 1 && !(nowSecond >= processsecond || dateProcess)) { // 如果已提醒过的是重复提醒，并且当前流程没有超时，继续判断本次是否应该提醒
								int repeathour = (Integer) map.get("repeathour");
								int repeatminute = (Integer) map.get("repeatminute");
								long repeatSecond = repeathour * 3600 + repeatminute * 60; // 重复间隔秒数
								
								long nowRepeatIndex = (nowSecond - remindsecond) / repeatSecond;
								long lastRemindRepeatIndex = (lastRemindSecond - remindsecond) / repeatSecond;
								if(nowRepeatIndex > lastRemindRepeatIndex) { // 判断本次重复提醒和上次重复提醒是否是同一个重复周期内，不是则提醒
									isremind = true;
								}
								/*
								if(isremind) { // 判断下一个提醒设置是否超时，如果超时，此重复提醒已超出作用范围，不作提醒
									for(int i = listIndex + 1; i < overTimeList.size(); i++) {
										HashMap nextMap = (HashMap) overTimeList.get(i);
										
										int nextRemindtype = (Integer) nextMap.get("remindtype");
				                    	if(nextRemindtype != 0) { // 后面不是超时前提醒，跳出判断
				                    		break;
				                    	}
				                    	
				                    	int nextRemindhour = (Integer) nextMap.get("remindhour");
			                        	int nextRemindminute = (Integer) nextMap.get("remindminute");
			                        	if(nextRemindhour == remindhour && nextRemindminute == remindminute) { // 提醒时间相同，继续循环下一条
			                        		continue;
			                        	}
			                        	
			                        	if(dateProcess) {
			                        		isremind = false;
			                        	}else {
				                        	long nextRemindsecond = 0;
				    						if(nodepasshour >= nextRemindhour) {
				    							if(nodepassminute >= nextRemindminute) {
				    								nextRemindsecond = (nodepasshour - nextRemindhour) * 3600 + (nodepassminute - nextRemindminute) * 60;
				    							}else{
				    								nextRemindsecond = (nodepasshour - nextRemindhour - 1) * 3600 + (60 + nodepassminute - nextRemindminute) * 60;
				    							}
				    						}
				    						
				    						if(nowSecond >= nextRemindsecond) {
				    							isremind = false;
				    						}
			                        	}
			                        	break;
									}
								}
								*/
							}
						}
						if(logBug) writeLog("isremindQ===="+isremind);
						if(isremind) { // 超时提醒
							map.put("sign", sign);
							map.put("signPos", signPos);
							map.put("requestid", requestid);
							map.put("nodeid", nodeid);
							map.put("userlist", userlist);
							map.put("usertypelist", usertypelist);
							map.put("agenttypelist", agenttypelist);
							map.put("agentorbyagentidlist", agentorbyagentidlist);
							map.put("language", language);
							map.put("workflowid", workflowid);
							
							lastRemindDatetimeMap.put(id, nowdatetime);
							map.put("nowdatetime", formatLastRemindDatetimeInfo(lastRemindDatetimeMap));
							doOverTimeRemind(map);
						}else {
							if(repeatremind == 0 && !hasLastRemind) { // 本次不作提醒的非重复提醒，如果之前没提醒过，说明未超时，后面的不再循环
								break;
							}
						}
                    }
                    //超时提醒--end
                    /***********************************************************************************************/
                    
                    //超时处理--begin
					if(logBug){ 
						writeLog("isprocessed===="+isprocessed);
						writeLog("processsecond===="+processsecond);
						writeLog("getOverTime===="+overTimeBean.getOverTime(currentdatetime,nowdatetime));
						writeLog("dateProcess===="+dateProcess);
					}
                    if(isprocessed==0 && (processsecond>0 && overTimeBean.getOverTime(currentdatetime,nowdatetime)>=processsecond || dateProcess)){
                    	if(isautoflow==1){//启用超时处理
                            if(flownextoperator==1){//自动流转
								if(logBug) writeLog("hasNeedInputField===="+hasNeedInputField(requestid,workflowid,nodeid));
                                if(!hasNeedInputField(requestid,workflowid,nodeid)){
									AutoFlowNextNode(requestid,nodeid,userlist,usertypelist,agenttypelist,agentorbyagentidlist,workflowtype,ProcessorOpinion);
                                    for(int i=0;i<idlist.size();i++){
                                    sql="update workflow_currentoperator set isreminded='1',isprocessed='2' where id="+idlist.get(i)+" and requestid="+requestid;
                                    rs2.executeSql(sql);
                                    }
                                }else{
                                    for(int i=0;i<idlist.size();i++){
                                        sql="update workflow_currentoperator set isreminded='1',isprocessed='3' where id="+idlist.get(i)+" and requestid="+requestid;
                                        rs2.executeSql(sql);
                                    }
                                }
								
                            }else if(flownextoperator==2){
                            	
                            }else if(flownextoperator ==5) { // 流程干预到上级
                                writeLog("流程干预到上级>>>>>requestid=" + requestid + ",flowobjectids=" + flowobjectids);
                                //ArrayList flowobjectlist=Util.TokenizerString(flowobjectids,",");
                                RecordSet rs9 = new RecordSet();
                                String s = "select stopuserid from workflow_nodelink where workflowid=? and nodeid=?";
                                //writeLog("干预终止用户查询=" + s);
                                rs9.executeQuery(s, workflowid, nodeid);
                                rs9.next();
                                String stopuserid = Util.null2String(rs9.getString("stopuserid"));

                                rs.executeProc("GetDBDateAndTime", "");
                                String currentdate = "", currenttime = "";
                                if (rs.next()) {
                                    currentdate = rs.getString("dbdate");
                                    currenttime = rs.getString("dbtime");
                                }

                                /*RequestOperationLogManager rolm = new RequestOperationLogManager(requestid
                                        , nodeid
                                        , 0
                                        , user.getUID()
                                        , user.getType()
                                        , currentdate
                                        , currenttime
                                        , "submit");
//开始记录日志
                                //rolm.flowTransStartBefore();*/
                                wfAgentCondition wfAgentCondition = new wfAgentCondition();
                                int opnumb = 0;    // 是否写签字意见
                                int opnume = 0;
                                String personStr = "";
                                RecordSetTrans rst = new RecordSetTrans();
                                rst.setAutoCommit(false);
                                try {
                                    rst.executeQuery("select count(1) from workflow_currentoperator where requestid=?", requestid);
                                    rst.next();
                                    opnumb = rst.getInt(1);
                                    for (int i = 0; i < idlist.size(); i++) {
                                        //sql="update workflow_currentoperator set isreminded='1',isprocessed='3' where id="+idlist.get(i)+" and requestid="+requestid;
                                        String managesql = "select userid from workflow_currentoperator where id=" + idlist.get(i) + " and requestid=" + requestid;
                                        //writeLog("查上级sql=" + managesql);
                                        rst.executeSql(managesql);
                                        rst.next();
                                        String uid = rst.getString(1);
                                        if (uid.equals(stopuserid)) {
                                            writeLog("未操作者=" + uid + ",是终止用户=" + stopuserid + ",不找上级");
                                            sql = "update workflow_currentoperator set isreminded='1',isprocessed='3' where id=" + idlist.get(i) + " and requestid=" + requestid;
                                            writeLog("更新为已超时处理过<防止重复扫描>=requestid=" + requestid);
                                            rst.executeSql(sql);
                                            continue;
                                        }
                                        String manageid = resource.getManagerID(uid);
                                        if (manageid.isEmpty()) {
                                            writeLog("未找到上级，当前未操作者设为已超时处理过=" + uid + ",requestid=" + requestid);
                                            sql = "update workflow_currentoperator set isreminded='1',isprocessed='3' where id=" + idlist.get(i) + " and requestid=" + requestid;
                                            rst.executeSql(sql);
                                            continue;
                                        }

                                        // 判断代理情况
                                        String agenterId = "";
                                        String agentCheckSql = " select * from workflow_agentConditionSet where workflowId=" + workflowid + " and bagentuid=" + manageid +
                                                " and agenttype = '1' and isproxydeal='1'  " +
                                                " and ( ( (endDate = '" + currentdate + "' and (endTime='' or endTime is null))" +
                                                " or (endDate = '" + currentdate + "' and endTime > '" + currenttime + "' ) ) " +
                                                " or endDate > '" + currentdate + "' or endDate = '' or endDate is null)" +
                                                " and ( ( (beginDate = '" + currentdate + "' and (beginTime='' or beginTime is null))" +
                                                " or (beginDate = '" + currentdate + "' and beginTime < '" + currenttime + "' ) ) " +
                                                " or beginDate < '" + currentdate + "' or beginDate = '' or beginDate is null) order by agentbatch asc  ,id asc ";

                                        rs3.execute(agentCheckSql);
                                        while (rs3.next()) {
                                            String agentid = rs3.getString("agentid");
                                            String conditionkeyid = rs3.getString("conditionkeyid");
                                            boolean isagentcond = wfAgentCondition.isagentcondite("" + requestid, "" + workflowid, manageid, agentid, conditionkeyid);
                                            if (isagentcond) {
                                                agenterId = rs3.getString("agentuid");
                                                break;
                                            }
                                        }

                                        if (!agenterId.isEmpty()) {
                                            // 插入上级已办、同时插入代理人待办
                                            writeLog("requestid=" + requestid + ",cid=" + idlist.get(i) + ",manageid=" + manageid + ",上级有代理=" + agenterId + ",--------");
                                            // 判断是否有待办  更新islasttimes  。。。。。


                                            String sqls = "insert into workflow_currentoperator\n" +
                                                    "(requestid,userid,groupid,workflowid,workflowtype,isremark,usertype,nodeid,agentorbyagentid,agenttype,receivedate,receivetime,viewtype,iscomplete,islasttimes,groupdetailid,showorder) \n" +
                                                    "select  \n" +
                                                    "requestid,'" + manageid + "',groupid,workflowid,workflowtype,2,usertype,nodeid,'" + agenterId + "','" + 1 + "','" + currentdate + "','" + currenttime + "',0,iscomplete,islasttimes,groupdetailid,'-99' from workflow_currentoperator \n" +
                                                    "where id=" + idlist.get(i) + " and requestid=" + requestid;

                                            rst.executeSql(sqls);

                                            sqls = "insert into workflow_currentoperator\n" +
                                                    "(requestid,userid,groupid,workflowid,workflowtype,isremark,usertype,nodeid,agentorbyagentid,agenttype,receivedate,receivetime,viewtype,iscomplete,islasttimes,groupdetailid,showorder) \n" +
                                                    "select  \n" +
                                                    "requestid,'" + agenterId + "',groupid,workflowid,workflowtype,0,usertype,nodeid,'" + manageid + "','" + 2 + "','" + currentdate + "','" + currenttime + "',0,iscomplete,islasttimes,groupdetailid,'-99' from workflow_currentoperator \n" +
                                                    "where id=" + idlist.get(i) + " and requestid=" + requestid;
                                            rst.executeSql(sqls);

                                            personStr += resource.getLastname(manageid) + "->" + resource.getLastname(agenterId) + ",";
                                        } else {
                                            // 插入上级
                                            // 插入新的操作者，
                                            writeLog("requestid=" + requestid + ",cid=" + idlist.get(i) + ",manageid=" + manageid + ",上级无代理----");
                                            String sqls = "insert into workflow_currentoperator\n" +
                                                    "(requestid,userid,groupid,workflowid,workflowtype,isremark,usertype,nodeid,agentorbyagentid,agenttype,receivedate,receivetime,viewtype,iscomplete,islasttimes,groupdetailid,showorder) \n" +
                                                    "select  \n" +
                                                    "requestid,'" + manageid + "',groupid,workflowid,workflowtype,0,usertype,nodeid,'-1','0','" + currentdate + "','" + currenttime + "',0,iscomplete,islasttimes,groupdetailid,'-99' from workflow_currentoperator \n" +
                                                    "where id=" + idlist.get(i) + " and requestid=" + requestid;
                                            rst.executeSql(sqls);
                                            personStr += resource.getLastname(manageid) + ",";
                                        }

                                        // sq 当前用户设为已办
                                        sql = "update workflow_currentoperator set isremark='2',preisremark='0',operatedate='" + currentdate + "',operatetime='" + currenttime + "' where id=" + idlist.get(i) + " and requestid=" + requestid;
                                        rst.executeSql(sql);


                                    }
                                    rst.executeQuery("select count(1) from workflow_currentoperator where requestid=?", requestid);
                                    rst.next();
                                    opnume = rst.getInt(1);
                                    rst.commit();
                                }catch (Exception e){
                                    rst.setAutoCommit(true);
                                    rst.rollback();
                                    e.printStackTrace();
                                }


                                // 冲刷islasttimes
                                try {
                                    //把这个流程的所有操作者都找一遍，只要islasttimes有2个为1，就要清掉。不管客户的
                                    RecordSet rs001 = new RecordSet();
                                    RecordSet rs002 = new RecordSet();
                                    String sqltmp = "select userid, islasttimes, id, isremark from workflow_currentoperator where usertype=0 and requestid=" + requestid + " order by userid, islasttimes,case isremark when '4' then '1.5' when '9' then '1.4' else isremark end desc, id asc";
                                    ArrayList userid2List = new ArrayList();
                                    ArrayList islasttimes2List = new ArrayList();
                                    ArrayList id2List = new ArrayList();
                                    ArrayList isRemark2List = new ArrayList();
                                    rs001.execute(sqltmp);
                                    while (rs001.next()) {
                                        int userid_tmp = Util.getIntValue(rs001.getString("userid"), 0);
                                        int islasttimes_tmp = Util.getIntValue(rs001.getString("islasttimes"), 0);
                                        int id_tmp = Util.getIntValue(rs001.getString("id"), 0);
                                        userid2List.add("" + userid_tmp);
                                        islasttimes2List.add("" + islasttimes_tmp);
                                        id2List.add("" + id_tmp);
                                        isRemark2List.add(Util.null2String(rs001.getString("isremark")));
                                    }
                                    int userid_t = 0;
                                    int islasttimes_t = -1;
                                    int id_t = 0;
                                    int isremark_t = -1;
                                    for (int cx = 0; cx < userid2List.size(); cx++) {
                                        int userid_tmp = Util.getIntValue((String) userid2List.get(cx), 0);
                                        int islasttimes_tmp = Util.getIntValue((String) islasttimes2List.get(cx), 0);
                                        int id_tmp = Util.getIntValue((String) id2List.get(cx), 0);
                                        int isremark_tmp = Util.getIntValue((String) isRemark2List.get(cx));
                                        if (userid_tmp == userid_t) {
                                            if (islasttimes_t == 1 && islasttimes_tmp == 1) {//同用户，2个islasttimes
                                                sqltmp = "update workflow_currentoperator set islasttimes=0 where id=" + id_t;
                                                rs002.execute(sqltmp);
                                            } else if (islasttimes_t == 0 && islasttimes_tmp == 1 && isremark_t == 0 && (isremark_tmp == 8 || isremark_tmp == 9)) {
                                                sqltmp = "update workflow_currentoperator set islasttimes=1 where id=" + id_t;
                                                rs002.execute(sqltmp);
                                                sqltmp = "update workflow_currentoperator set islasttimes=0 where id=" + id_tmp;
                                                rs002.execute(sqltmp);
                                            }
                                        } else {
                                            if (islasttimes_t == 0) {//已经是不同用户了，前面用户最后一个的islasttimes还是0
                                                sqltmp = "update workflow_currentoperator set islasttimes=1 where id=" + id_t;
                                                rs002.execute(sqltmp);
                                            }

                                        }
                                        userid_t = userid_tmp;
                                        islasttimes_t = islasttimes_tmp;
                                        id_t = id_tmp;
                                        isremark_t = isremark_tmp;
                                    }
                                    if (islasttimes_t == 0) {//考虑最后一个情况，如果islasttimes是0
                                        sqltmp = "update workflow_currentoperator set islasttimes=1 where id=" + id_t;
                                        rs002.execute(sqltmp);
                                    }
                                    if (new WFLinkInfo().getNodeAttribute(nodeid) == 2) {
                                        new RequestManager().CheckUserIsLasttimes(requestid, nodeid, user);
                                    }
                                } catch (Exception e) {
                                    writeLog(e);
                                }

                                //rolm.flowTransSubmitAfter();
                                writeLog("判断是否写签字意见=" + (opnumb != opnume));
                                if (opnumb != opnume) {
                                    // 插入签字意见
                                    char flag = Util.getSeparator();

                                    String nodeType = new WFLinkInfo().getNodeType(nodeid);
                                    String logtype = "";
                                    if ("1".equals(nodeType)) {
                                        logtype = "0";
                                    } else {
                                        logtype = "2";
                                    }
                                    // 查当前操作者的代理情况
                                    String curusersql = "select agentorbyagentid,agenttype from workflow_currentoperator where userid=? and isremark='2' order by id desc";
                                    rs2.executeQuery(curusersql, userid);
                                    rs2.next();
                                    String agentorbyagentid = rs2.getString("agentorbyagentid");
                                    String agenttype = rs2.getString("agenttype");

                                    String Procpara = "";
                                    if (agentorbyagentid.equals("-1")) {
                                        Procpara = "" + requestid + flag + workflowid + flag + nodeid + flag + logtype + flag + currentdate + flag + currenttime + flag + userid + flag + "127.0.0.1" + flag + usertype + flag + nodeid + flag + personStr.trim() + flag + "-1" + flag + "0" + flag + "1" + flag + "" + flag + "0" + flag + "" + flag + "" + flag + "" + flag + "" + flag + "" + flag + "" + flag + "";
                                        writeLog("无代理写签字意见=" + Procpara + ",意见内容=" + ProcessorOpinion);
                                    } else {
                                        Procpara = "" + requestid + flag + workflowid + flag + nodeid + flag + logtype + flag + currentdate + flag + currenttime + flag + userid + flag + "127.0.0.1" + flag + usertype + flag + nodeid + flag + personStr.trim() + flag + agentorbyagentid + flag + "2" + flag + "1" + flag + "" + flag + "0" + flag + "" + flag + "" + flag + "" + flag + "" + flag + "" + flag + "" + flag + "";
                                        writeLog("有代理=" + agentorbyagentid + ",被代理人（当前操作人）=" + userid + ",写签字意见=" + Procpara + ",意见内容=" + ProcessorOpinion);
                                    }

                                    new RequestManager().execRequestlog(Procpara, rs3, flag, ProcessorOpinion);
                                }






                                /*if (manageids.size() >= 1) {


                                    //setOperator(manageids,requestid,workflowid,workflowtype,nodeid);
                                    RequestManager requestManager = new RequestManager();
                                    requestManager.setSrc("intervenor");
                                    requestManager.setIscreate("0");
                                    requestManager.setRequestid(requestid);
                                    requestManager.setWorkflowid(workflowid);
                                    WorkflowComInfo wci = new WorkflowComInfo();
                                    requestManager.setWorkflowtype(wci.getWorkflowtype(workflowid + ""));
                                    requestManager.setIsremark(0);
                                    rs3.executeSql("select isbill,formid from workflow_base where id=" + workflowid);
                                    int isbill = 0;
                                    int formid = 0;
                                    int billid = 0;
                                    String billtablename = "";
                                    if (rs3.next()) {
                                        isbill = Util.getIntValue(rs3.getString("isbill"), 0);
                                        formid = Util.getIntValue(rs3.getString("formid"), 0);
                                    }
                                    rs3.executeSql("select tablename from workflow_bill where id=" + formid);
                                    if (rs3.next()) {
                                        billtablename = Util.null2String(rs3.getString("tablename"));
                                    }
                                    if (!billtablename.trim().equals("")) {
                                        rs3.executeSql("select id from " + billtablename + " where requestid=" + requestid);
                                        if (rs3.next()) {
                                            billid = Util.getIntValue(rs3.getString("id"));
                                        }
                                    }
                                    writeLog("formid=" + formid + ",isbill=" + isbill + ",billid=" + billid + ",nodeid=" + nodeid);
                                    WFLinkInfo wfLinkInfo = new WFLinkInfo();
                                    String nodeType = wfLinkInfo.getNodeType(nodeid);
                                    requestManager.setFormid(formid);
                                    requestManager.setIsbill(isbill);
                                    requestManager.setBillid(billid);
                                    requestManager.setNodeid(nodeid);
                                    requestManager.setNodetype(nodeType);
                                    requestManager.setRequestname(requestcominfo.getRequestname(requestid + ""));
                                    rs3.executeQuery("select requestlevel from workflow_requestbase where requestid=?", requestid);
                                    rs3.next();
                                    String requestlevel = rs3.getString("requestlevel");
                                    requestManager.setRequestlevel(requestlevel);
                                    requestManager.setRemark(ProcessorOpinion);
                                    //requestManager.setRequest(fu) ;
                                    requestManager.setSubmitNodeId(nodeid + "_" + nodeType + "_0");
                                    String Intervenorid = "";
                                    for (String str : manageids) {
                                        Intervenorid += "," + str;
                                    }
                                    Intervenorid = Intervenorid.isEmpty() ? "" : Intervenorid.substring(1);
                                    writeLog("Intervenorid=" + Intervenorid);
                                    requestManager.setIntervenorid(Intervenorid);
                                    requestManager.setSignType(1);
//System.out.println("messageTypr===="+messageType);
                                    //requestManager.setIsFromEditDocument(isFromEditDocument) ;
                                    requestManager.setUser(user);
                                    requestManager.setIsagentCreater(0);
                                    //requestManager.setBeAgenter(beagenter);   // 干预不用
                                    //requestManager.setIsPending(ispending);
                                    //requestManager.setRequestKey(wfcurrrid);
                                    //requestManager.setCanModify(IsCanModify);
                                    //requestManager.setCoadsigntype(coadsigntype);
                                    requestManager.setEnableIntervenor(1);//是否启用节点及出口附加操作
                                    //requestManager.setRemarkLocation(remarkLocation);
                                    //requestManager.setIsFirstSubmit(isFirstSubmit);   // 干预用不到
                                    requestManager.flowNextNode();*/



                                   /* for(int i=0;i<idlist.size();i++){
                                        sql="update workflow_currentoperator set isreminded='1',isprocessed='3' where id="+idlist.get(i)+" and requestid="+requestid;
                                        rs2.executeSql(sql);
                                    }
                                }else{
                                    // 当前未操作者改为超时数据
                                    writeLog("未找到上级，当前未操作者设为已超时处理过");
                                    for(int i=0;i<idlist.size();i++){
                                        sql="update workflow_currentoperator set isreminded='1',isprocessed='3' where id="+idlist.get(i)+" and requestid="+requestid;
                                        rs2.executeSql(sql);
                                    }
                                }*/
                                writeLog("流程干预到上级<<<<<requestid=" + requestid);
                            }else if(flownextoperator==3){//流程退回





                            	if(!"".equals(flowobjectreject)){
                            		FlowNode(requestid, userid, ProcessorOpinion, "", Integer.parseInt(flowobjectreject));
                            		writeLog("超时退回 :requestid="+requestid);
                            	}
                            	
                            }else if(flownextoperator==4){//流程提交
                            	if(!"".equals(flowobjectsubmit)){
                            		WorkflowNodeFlow  wnf = new WorkflowNodeFlow();
                            		wnf.setRequestid(requestid);
                                    wnf.setNodeid(nodeid);
                                    wnf.setFlowobjectsubmit(Integer.parseInt(flowobjectsubmit));
                                    //wnf.setNodetype(nodetype);
                                    wnf.setWorkflowid(workflowid);
                                    wnf.setWorkflowtype(workflowtype);
                                    wnf.setUserid(userid);
                                    wnf.setUsertype(usertype);
                                    wnf.setUser(user);
                                    wnf.setRemark(ProcessorOpinion);
                                    //wnf.setCreater(creater);
                                    //wnf.setCreatertype(creatertype);
                                    //wnf.setIsbill(isbill);
                                    //wnf.setBillid(billid);
                                    //wnf.setFormid(formid);
                                    wnf.setBilltablename("");
                                    boolean flag = wnf.flowNextNode();
                                    
                                    //System.out.println("超时提交至目标节点:requestid="+requestid+" flag:"+flag);
                                    writeLog("超时提交至目标节点:requestid="+requestid);
                            	}
                            }else{//指定干预对象
                                //log.debug("超时处理:流转至干预对象---begin");
                                System.out.println("flowobjectids:"+flowobjectids);
                                ArrayList flowobjectlist=Util.TokenizerString(flowobjectids,",");
                                setOperator(flowobjectlist,requestid,workflowid,workflowtype,nodeid);
                                Calendar today = Calendar.getInstance();
                                String currentdate = Util.add0(today.get(Calendar.YEAR), 4) + "-" +
                                        Util.add0(today.get(Calendar.MONTH) + 1, 2) + "-" +
                                        Util.add0(today.get(Calendar.DAY_OF_MONTH), 2);

                                String currenttime = Util.add0(today.get(Calendar.HOUR_OF_DAY), 2) + ":" +
                                        Util.add0(today.get(Calendar.MINUTE), 2) + ":" +
                                        Util.add0(today.get(Calendar.SECOND), 2);
                                //获得数据库服务器当前时间
                                if(rs5.getDBType().equals("oracle")){
                                    sql="select to_char(sysdate,'yyyy-mm-dd') currentdate,to_char(sysdate,'hh24:mi:ss') currenttime from dual";
                                }else{
                                    sql="select convert(char(10),getdate(),20) currentdate,convert(char(8),getdate(),108) currenttime";
                                }
                                rs5.executeSql(sql);
                                if(rs5.next()){
                                    currentdate=rs5.getString("currentdate");
                                    currenttime=rs5.getString("currenttime");
                                }
                                for(int i=0;i<userlist.size();i++){
									writeWFLog(requestid,workflowid,nodeid,Util.getIntValue((String)userlist.get(i)),Util.getIntValue((String)usertypelist.get(i)),Util.getIntValue((String)agenttypelist.get(i)),Util.getIntValue((String)agentorbyagentidlist.get(i)),nodeid,currentdate,currenttime,ProcessorOpinion,"7",false,0);
                                    
									/**日志权限处理start**/
									int logtypelog = 7;   //超时的日志
									int useridlog = Util.getIntValue((String)userlist.get(i));
									boolean isCurrentNode = isCurrentNode(requestid, nodeid);
									if(isCurrentNode){
										String logsql = " select logid from workflow_requestlog where workflowid = " 
											+ workflowid
											+ " and nodeid = "
											+ nodeid
											+ " and logtype = '"
											+ logtypelog
											+ "' and requestid = "
											+ requestid
											+ " and operatedate = '"
											+ currentdate
											+ "' and operatetime = '"
											+ currenttime
											+ "' and operator = " + useridlog
											+ " order by logid desc";

										rs3.executeSql(logsql);
										int logid = -1;
										if (rs3.next()) {
											logid = rs3.getInt("logid");
										}
										
										String logusers = String.valueOf(useridlog);
										//指定干预对象也需要赋予日志的查看权限
										wfAgentCondition wfAgentCondition=new wfAgentCondition();
										
										RequestRemarkRight remarkRight = new RequestRemarkRight();
										remarkRight.setRequestid(requestid);
										remarkRight.setNodeid(nodeid);
										int currropid = Util.getIntValue(String.valueOf(idlist.get(i)),-1);
										remarkRight.setWorkflow_currentid(currropid);
										System.out.println("flowobjectlist=" + flowobjectlist);
										for(int at=0;at<flowobjectlist.size();at++){
											String flowobject = String.valueOf(flowobjectlist.get(at));
											String agenterId="";
											 String agentCheckSql = " select * from workflow_agentConditionSet where workflowId="+ workflowid +" and bagentuid=" + flowobject +
							                 " and agenttype = '1' and isproxydeal='1'  " +
							                 " and ( ( (endDate = '" + currentdate + "' and (endTime='' or endTime is null))" +
							                 " or (endDate = '" + currentdate + "' and endTime > '" + currenttime + "' ) ) " +
							                 " or endDate > '" + currentdate + "' or endDate = '' or endDate is null)" +
							                 " and ( ( (beginDate = '" + currentdate + "' and (beginTime='' or beginTime is null))" +
							                 " or (beginDate = '" + currentdate + "' and beginTime < '" + currenttime + "' ) ) " +
							                 " or beginDate < '" + currentdate + "' or beginDate = '' or beginDate is null) order by agentbatch asc  ,id asc ";
											 
											 rs3.execute(agentCheckSql);
							                 while(rs3.next()){
												String agentid = rs3.getString("agentid");
												String conditionkeyid = rs3.getString("conditionkeyid");
												boolean isagentcond = wfAgentCondition.isagentcondite("" + requestid,"" + workflowid,flowobject,agentid,conditionkeyid);
												if (isagentcond) {
													agenterId = rs3.getString("agentuid");
													break;
												}
							                 }
							                 //每个干预对象有查看日志的权限
							                 if(!"".equals(flowobject) && !"-1".equals(flowobject)){
							                	 logusers += "," + flowobject;
							                 }
							                 //代理人有查看日志的权限
							                 if(!"".equals(agenterId)){
							                	 logusers += "," + agenterId;
							                 }
										}
										System.out.println("logusers=" + logusers);
										if(logusers.endsWith(",")){
											logusers = logusers.substring(0,logusers.length() - 1);
										}
										//保存权限
										remarkRight.saveRemarkRight(logid, logusers);
									}
									/**日志权限处理end**/
									
                                    sql="update workflow_currentoperator set isreminded='1',isprocessed='2' where id="+idlist.get(i)+" and requestid="+requestid;
                                    rs2.executeSql(sql);
                                }
                                //log.debug("超时处理:流转至干预对象---end");
                            }
                            
                        }else{
                            for(int i=0;i<idlist.size();i++){
                            sql="update workflow_currentoperator set isreminded='1',isprocessed='3' where id="+idlist.get(i)+" and requestid="+requestid;
                            rs2.executeSql(sql);
                            }
                        }
                    	//主流程自动流转
                    	SubWorkflowTriggerService.judgeMainWfAutoFlowNextNode(requestid);
                    }
                    //超时处理--end
                    
                  //资产超时后解冻处理
                    CptWfUtil cptWfUtil = new CptWfUtil();
            		String cptwftype = cptWfUtil.getWftype("" + workflowid);
            		sql="select wb.formid from workflow_requestbase wr,workflow_base wb where wr.workflowid=wb.id and wr.requestid="+requestid;
            		rs2.executeSql(sql);
            		rs2.next();
            		int formid = Util.getIntValue(rs2.getString("formid"));
            		if (!"".equals(cptwftype) && !"apply".equalsIgnoreCase(cptwftype)) {
            			rs.executeSql("update CptCapital set frozennum = 0 where isdata='2' ");
            		}else if (formid==19||formid==201||formid==220||formid==220) {
            		    rs.executeSql("update CptCapital set frozennum = 0 where isdata='2' ");
            		}
                    
                  //超时后提醒--begin
                    for(listIndex = listIndex; listIndex < overTimeList.size(); listIndex++) {
                    	HashMap map = (HashMap) overTimeList.get(listIndex);
                    	
                    	int remindtype = (Integer) map.get("remindtype");
                    	if(remindtype != 1) { // 只提醒超时后提醒
                    		continue;
                    	}
                    	
                    	int id = (Integer) map.get("id");
                    	if(id <= 0) {
                    		continue;
                    	}
                    	
                    	String lastRemindDatetime = lastRemindDatetimeMap.get(id) == null ? "2000-01-01 00:00:00" : (String) lastRemindDatetimeMap.get(id);
                    	long lastRemindSecond = overTimeBean.getOverTime(currentdatetime,lastRemindDatetime); // 接收时间至上次提醒时间之间的秒数
                    	if(lastRemindSecond < 0) {
                    		lastRemindSecond = 0;
                    	}
                    	
                    	int remindhour = (Integer) map.get("remindhour");
                    	int remindminute = (Integer) map.get("remindminute");
                    	
                    	long remindsecond = processsecond + remindhour * 3600 + remindminute * 60; // 超时后提醒时间
                    	
						int repeatremind = (Integer) map.get("repeatremind"); // 是否重复提醒。1-是，0-不是
						boolean isremind = false; // 是否提醒
						boolean hasLastRemind = false; // 此提醒设置之前是否提醒过
						if(logBug){ 
							writeLog("nowSecondH===="+nowSecond);
							writeLog("remindsecondH===="+remindsecond);
						}
						if(nowSecond >= remindsecond) { // 判断本次是否提醒
							isremind = true;
						}
						if(isremind && repeatremind != 1) {
							//if(repeatremind == 1) { // 此重复提醒已超出作用范围，不作提醒
								//isremind = false;
							//}else { // 判断上一次超时计划任务执行时是否已超时，已超时则应该提醒过，本次不提醒
								if(!"".equals(lastRemindDatetime)) {
									if(lastRemindSecond >= remindsecond) { // 提醒过则不提醒
										isremind = false;
										hasLastRemind = true;
									}
								}
							//}
						}else {
							if(repeatremind == 1) { // 重复提醒判断是否以满足提醒周期，满足则提醒
								isremind = false;
								int repeathour = (Integer) map.get("repeathour");
								int repeatminute = (Integer) map.get("repeatminute");
								long repeatSecond = repeathour * 3600 + repeatminute * 60; // 重复间隔秒数
								
								long startRemindsecond = 0; // 重复提醒起始时间点
								/*
								for(int i = listIndex - 1; i >= 0; i--) { // 循环找出重复提醒起始时间点
									HashMap lastMap = (HashMap) overTimeList.get(i);
									
									int lastRemindtype = (Integer) lastMap.get("remindtype");
			                    	if(lastRemindtype != 1) { // 前面不是超时后提醒，跳出判断
			                    		break;
			                    	}
			                    	
			                    	int lastRemindhour = (Integer) lastMap.get("remindhour");
		                        	int lastRemindminute = (Integer) lastMap.get("remindminute");
		                        	if(lastRemindhour == remindhour && lastRemindminute == remindminute) { // 提醒时间相同，继续循环上一条
		                        		continue;
		                        	}
		                        	
		                        	startRemindsecond = processsecond + lastRemindhour * 3600 + lastRemindminute * 60;
		                        	break;
								}
								if(startRemindsecond == 0) {
									startRemindsecond = processsecond;
								}
								*/
								startRemindsecond = remindsecond;
								
								if(nowSecond >= startRemindsecond) { // 当前时间在重复作用范围内
									long nowRepeatIndex = (nowSecond - startRemindsecond) / repeatSecond;
									if(nowRepeatIndex >= 0) {
										isremind = true;
										
										if(!"".equals(lastRemindDatetime)) { // 如果之前提醒过，判断本次重复提醒和上次重复提醒是否是同一个重复周期内，不是则提醒
											if(lastRemindSecond >= startRemindsecond) {
												long lastRemindRepeatIndex = (lastRemindSecond - startRemindsecond) / repeatSecond;
												if(nowRepeatIndex <= lastRemindRepeatIndex) {
													isremind = false;
												}
											}
										}
									}
	    						}
							}
						}
						if(logBug) writeLog("Hisremind===="+isremind);
						if(isremind) { // 超时提醒
							map.put("sign", sign);
							map.put("signPos", signPos);
							map.put("requestid", requestid);
							map.put("nodeid", nodeid);
							map.put("userlist", userlist);
							map.put("usertypelist", usertypelist);
							map.put("agenttypelist", agenttypelist);
							map.put("agentorbyagentidlist", agentorbyagentidlist);
							map.put("language", language);
							map.put("workflowid", workflowid);
							
							lastRemindDatetimeMap.put(id, nowdatetime);
							map.put("nowdatetime", formatLastRemindDatetimeInfo(lastRemindDatetimeMap));
							doOverTimeRemind(map);
						}else {
							if(repeatremind == 0 && !hasLastRemind) { // 本次不作提醒的非重复提醒，如果之前没提醒过，说明未满足时间，后面的不再循环
								break;
							}
						}
                    }
                    //超时后提醒--end
                    /***********************************************************************************************/
//                }else{
//                    //找不到下一节点，不做超时处理





//                    for (int i = 0; i < idlist.size(); i++) {
//                        sql = "update workflow_currentoperator set isreminded='1',isprocessed='4' where id=" + idlist.get(i) + " and requestid=" + requestid;
//                        rs2.executeSql(sql);
//                    }
                }
            }
        }
    }
    

    
    // 获取所有出口的超时提醒信息
    public HashMap getNodeLinkOverTimeInfo() {
    	HashMap hashMap = new HashMap();
    	RecordSet rs = new RecordSet();
    	String sql = "select id, linkid, remindtype, remindhour, remindminute, repeatremind, repeathour, repeatminute, FlowRemind, MsgRemind "
    			+ ", MailRemind, ChatsRemind, InfoCentreRemind, CustomWorkflowid, isnodeoperator, iscreater, ismanager, isother, remindobjectids "
    			+ ", (case remindtype when 1 then (remindhour * 60 + remindminute) else -(remindhour * 60 + remindminute) end) as minute "
    			+ " from workflow_nodelinkOverTime order by workflowid, linkid, remindtype, minute, id ";
    	rs.executeSql(sql);
    	while(rs.next()) {
    		int linkid = Util.getIntValue(rs.getString("linkid"), 0);
    		if(linkid > 0) {
    			HashMap map = new HashMap();
    			map.put("id", Util.getIntValue(rs.getString("id"), 0));
    			map.put("remindtype", Util.getIntValue(rs.getString("remindtype"), -1));
    			map.put("remindhour", Util.getIntValue(rs.getString("remindhour"), 0));
    			map.put("remindminute", Util.getIntValue(rs.getString("remindminute"), 0));
    			map.put("repeatremind", Util.getIntValue(rs.getString("repeatremind"), 0));
    			map.put("repeathour", Util.getIntValue(rs.getString("repeathour"), 0));
    			map.put("repeatminute", Util.getIntValue(rs.getString("repeatminute"), 0));
    			map.put("FlowRemind", Util.getIntValue(rs.getString("FlowRemind"), 0));
    			map.put("MsgRemind", Util.getIntValue(rs.getString("MsgRemind"), 0));
    			map.put("MailRemind", Util.getIntValue(rs.getString("MailRemind"), 0));
    			map.put("ChatsRemind", Util.getIntValue(rs.getString("ChatsRemind"), 0));
    			map.put("InfoCentreRemind", Util.getIntValue(rs.getString("InfoCentreRemind"), 0));
    			map.put("CustomWorkflowid", Util.getIntValue(rs.getString("CustomWorkflowid"), 0));
    			map.put("isnodeoperator", Util.getIntValue(rs.getString("isnodeoperator"), 0));
    			map.put("iscreater", Util.getIntValue(rs.getString("iscreater"), 0));
    			map.put("ismanager", Util.getIntValue(rs.getString("ismanager"), 0));
    			map.put("isother", Util.getIntValue(rs.getString("isother"), 0));
    			map.put("remindobjectids", Util.null2String(rs.getString("remindobjectids")));
    			map.put("minute", Long.parseLong(rs.getString("minute")));
    			
      			ArrayList list = hashMap.get(linkid) == null ? new ArrayList() : (ArrayList) hashMap.get(linkid);
      			list.add(map);
      			
      			hashMap.put(linkid, list);
    		}
    	}
    	return hashMap;
    }
    
    // 获取上次提醒时间信息
    public HashMap<Integer, String> getLastRemindDatetimeInfo(String lastRemindDatetimeStr) {
    	HashMap<Integer, String> hashMap = new HashMap<Integer, String>();
    	if(!"".equals(lastRemindDatetimeStr)) {
        	String[] lastRemindDatetimeArr = lastRemindDatetimeStr.split(",");
        	for(int i = 0; i < lastRemindDatetimeArr.length; i++) {
        		String datetimeStr = Util.null2String(lastRemindDatetimeArr[i]).trim();
        		if(!"".equals(datetimeStr)) {
        			String[] datetimeArr = datetimeStr.split("_");
        			int id = Util.getIntValue(Util.null2String(datetimeArr[0]), 0);
        			String datetime = "";
        			if (datetimeArr.length > 1) {
        			    datetime = Util.null2String(datetimeArr[1]).trim();
        			}
        			if(id > 0) {
        				hashMap.put(id, datetime);
        			}
        		}
        	}
    	}
    	return hashMap;
    }
    
    // 格式化上次提醒时间信息，存入数据库
    public String formatLastRemindDatetimeInfo(HashMap<Integer, String> lastRemindDatetimeMap) {
    	String returnStr = "";
		for(Integer key : lastRemindDatetimeMap.keySet()) {
 			returnStr += "," + key + "_" + (String) lastRemindDatetimeMap.get(key);
		}
		if(!"".equals(returnStr)) {
			returnStr = returnStr.substring(1);
		}
		return returnStr;
    }
    
    // 超时提醒
    public void doOverTimeRemind(HashMap hashMap) {
    	int remindtype = (Integer) hashMap.get("remindtype"); // 0-超时前提醒，1-超时后提醒
    	int FlowRemind = (Integer) hashMap.get("FlowRemind");
    	int MsgRemind = (Integer) hashMap.get("MsgRemind");
    	int MailRemind = (Integer) hashMap.get("MailRemind");
    	int ChatsRemind = (Integer) hashMap.get("ChatsRemind");
    	int InfoCentreRemind = (Integer) hashMap.get("InfoCentreRemind");
    	int CustomWorkflowid = (Integer) hashMap.get("CustomWorkflowid");
        int isnodeoperator = (Integer) hashMap.get("isnodeoperator");
        int iscreater = (Integer) hashMap.get("iscreater");
        int ismanager = (Integer) hashMap.get("ismanager");
        int isother = (Integer) hashMap.get("isother");
        String remindobjectids = (String) hashMap.get("remindobjectids");
        
        String sign =  (String) hashMap.get("sign");
        String signPos =  (String) hashMap.get("signPos");
        int requestid = (Integer) hashMap.get("requestid");
        int nodeid = (Integer) hashMap.get("nodeid");
        ArrayList userlist = (ArrayList) hashMap.get("userlist");
        ArrayList usertypelist = (ArrayList) hashMap.get("usertypelist");
        ArrayList agenttypelist = (ArrayList) hashMap.get("agenttypelist");
        ArrayList agentorbyagentidlist = (ArrayList) hashMap.get("agentorbyagentidlist");
        int language = (Integer) hashMap.get("language");
        String nowdatetime = (String) hashMap.get("nowdatetime");
        
        int userid = 0;
        int usertype = 0;
        String sql = "";
        
        String remindusers="";
        String usertypes="";
        boolean mailsend=false;
        boolean msgsend=false;
        boolean wfsend=false;
        boolean chatssend=false;//微信提醒(QC:98106)
        boolean wfcreate = false;
        wfremindusers=new ArrayList();
        wfusertypes=new ArrayList();
        
     // sq add 自定义提醒人  节点操作者安全级别、节点操作者提醒人、角色
        RecordSet rssq = new RecordSet();
        String id = nowdatetime.split("_")[0];
        String sqlsq = "select aqjb,aqjbselect,aqjb_min,aqjb_max,jz,jzselect,jzqzselect,js,jsselect,jsjb from workflow_nodelinkOverTime where id=?";
        rssq.executeQuery(sqlsq, id);
        rssq.next();
        String aqjb = Util.null2String(rssq.getString("aqjb"));
        String aqjbselect = Util.null2String(rssq.getString("aqjbselect"));  
        String aqjb_min= Util.null2String(rssq.getString("aqjb_min"));
        String aqjb_max=Util.null2String(rssq.getString("aqjb_max")); 
        String jz=Util.null2String(rssq.getString("jz")); 
        String jzselect=Util.null2String(rssq.getString("jzselect"));  
        String jzqzselect = Util.null2String(rssq.getString("jzqzselect"));     
        String js = Util.null2String(rssq.getString("js"));    
        String jsselect = Util.null2String(rssq.getString("jsselect"));
        String jsjb = Util.null2String(rssq.getString("jsjb"));
		// 查询当前代办人：
        String dbsql = "select userid from workflow_currentoperator WHERE requestid=? AND isremark=0";
        String dbrs = "";	// 代办人集合
        rssq.executeQuery(dbsql, requestid);
        while(rssq.next()){
        	dbrs += "," + rssq.getString(1);
        }
        dbrs = dbrs.isEmpty() ? "" : dbrs.substring(1);
        String txsql = "";
        RecordSet rssq2 = new RecordSet();
        // 所有待办人所属分部和部门
        String dbrsql = "select subcompanyid1,departmentid from hrmresource where id in ("+dbrs+")";
        // 安全级别校验
		if("1".equals(aqjb)){
			rssq.executeQuery(dbrsql);
			while(rssq.next()){
				String fbid = rssq.getString(1);
				String bmid = rssq.getString(2);
				// 0 分部 1 部门
				String sqlflag = aqjbselect.equals("0") ? "subcompanyid1" : "departmentid";
				rssq2.executeQuery("select id from hrmresource where " + sqlflag +"=? and seclevel between ? and ? ", aqjbselect.equals("0") ? fbid : bmid,aqjb_min,aqjb_max);
				while(rssq2.next()){
					remindusers+="," +rssq2.getString(1);
	                usertypes+=",0";
				}
				
			}
		}
		// 矩阵校验
		if("1".equals(jz)){
			rssq.executeQuery(dbrsql);
			while(rssq.next()){
				String fbid = rssq.getString(1);
				String bmid = rssq.getString(2);
				// 1分部 2部门
				rssq2.executeQuery("SELECT txr FROM matrixtable_"+jzselect+" WHERE ID=? ", jzselect.equals("1") ? fbid : bmid);
				rssq2.next();
				remindusers+="," +Util.null2String(rssq2.getString(1));
                usertypes+=",0";
			}
			
			
		}
		//  角色校验
		if("1".equals(js)){
			txsql = "SELECT a.resourceid FROM hrmrolemembers a LEFT JOIN hrmroles b ON a.roleid=b.id WHERE b.id=? AND a.rolelevel=?";
			rssq.executeQuery(txsql, jsselect,jsjb);
			while(rssq.next()){
        		remindusers+="," +rssq.getString(1);
                usertypes+=",0";
        	}
		}
		remindusers = remindusers.isEmpty() ? "" : remindusers.substring(1);
		usertypes = usertypes.isEmpty() ? "" : usertypes.substring(1);
		String[] txrs = remindusers.split(",");
        writeLog("增加提醒人：" + txrs);
        
        if(FlowRemind==1){//流程提醒方式
            PoppupRemindInfoUtil popUtil=new PoppupRemindInfoUtil();
            if(isnodeoperator==1){//本节点操作人本人
                for(int k=0;k<userlist.size();k++){
                    int agenttype=Util.getIntValue((String)agenttypelist.get(k));
                    int agentorbyagentid=Util.getIntValue((String)agentorbyagentidlist.get(k));
                    userid=Util.getIntValue((String)userlist.get(k));
                    usertype=Util.getIntValue((String)usertypelist.get(k));
                    //本节点操作人提醒
                    if(userid>0){
                        //popUtil.addPoppupRemindInfo(userid,10,""+usertype,requestid);
                        if((","+remindusers).indexOf(","+userid+",")<0){
                            remindusers+=userid+",";
                            usertypes+=usertype+",";
                        }
                    }
                    //代理人提醒
                    if(agenttype>0 && agentorbyagentid>0){
                        //popUtil.addPoppupRemindInfo(agentorbyagentid,10,"0",requestid);
                        if((","+remindusers).indexOf(","+agentorbyagentid+",")<0){
                            remindusers+=agentorbyagentid+",";
                            usertypes+="0,";
                        }
                    }
                }
            }
            if(iscreater==1){//创建人
                sql="select creater,creatertype from workflow_requestbase where requestid="+requestid;
                rs2.executeSql(sql);
                if(rs2.next()){
                    int creatertmp=Util.getIntValue(rs2.getString("creater"),0);
                    //popUtil.addPoppupRemindInfo(creatertmp,10,Util.getIntValue(rs2.getString("creatertype"),0)+"",requestid);
                    if((","+remindusers).indexOf(","+creatertmp+",")<0){
                        remindusers+=creatertmp+",";
                        usertypes+=rs2.getString("creatertype")+",";
                    }
                }
            }
            if(ismanager==1){//本节点操作人经理
                int managerid=0;
                for(int k=0;k<userlist.size();k++){
                    int agenttype=Util.getIntValue((String)agenttypelist.get(k));
                    int agentorbyagentid=Util.getIntValue((String)agentorbyagentidlist.get(k));
                    userid=Util.getIntValue((String)userlist.get(k));
                    usertype=Util.getIntValue((String)usertypelist.get(k));
                    if(usertype==0){
                        managerid=Util.getIntValue(resource.getManagerID(userid+""),0);
                    }else{
                        managerid=Util.getIntValue(crminfo.getCustomerInfomanager(userid+""),0);
                    }
                    if(managerid>0){
                         //popUtil.addPoppupRemindInfo(managerid,10,"0",requestid);
                        if((","+remindusers).indexOf(","+managerid+",")<0){
                            remindusers+=managerid+",";
                            usertypes+="0,";
                        }
                    }
                }
            }
            if(isother==1){//指定对象
                ArrayList remindobjectlist=Util.TokenizerString(remindobjectids,",");
                for(int i=0;i<remindobjectlist.size();i++){
                    int tempid=Util.getIntValue((String)remindobjectlist.get(i));
                    //popUtil.addPoppupRemindInfo(tempid,10,"0",requestid);
                    if((","+remindusers).indexOf(","+tempid+",")<0){
                        remindusers+=tempid+",";
                        usertypes+="0,";
                    }
                }
            }
            if(remindusers.length()>1){
                String tempremindusers=remindusers.substring(0,remindusers.length()-1);
                String tempusertypes=usertypes.substring(0,usertypes.length()-1);
                ArrayList templist=Util.TokenizerString(tempremindusers,",");
                ArrayList tempusertypelist=Util.TokenizerString(tempusertypes,",");
                if(remindtype == 0) { // 超时前提醒
	                wfremindusers=templist;
	                wfusertypes=tempusertypelist;
	                rs2.executeSql("update workflow_currentoperator set wfreminduser='"+tempremindusers+"',wfusertypes='"+tempusertypes+"' where isremark='0' and requestid="+requestid);
                }else { // 超时后提醒
                	rs2.executeSql("update workflow_currentoperator set wfreminduser_csh='"+tempremindusers+"',wfusertypes_csh='"+tempusertypes+"' where isremark='0' and requestid="+requestid);
                }
                writeLog("系统消息提醒人：" + templist.toArray());
                for(int i=0;i<templist.size();i++){
                    if(wfsend){
                        popUtil.addPoppupRemindInfo(Util.getIntValue((String)templist.get(i)),10,(String)tempusertypelist.get(i),requestid,requestcominfo.getRequestname(requestid+""));
                    }else{
                        wfsend=popUtil.addPoppupRemindInfo(Util.getIntValue((String)templist.get(i)),10,(String)tempusertypelist.get(i),requestid,requestcominfo.getRequestname(requestid+""));
                    }
                }
            }else{
                wfsend=true;
            }
        }

        /***********************************************************************************************/
        if(MsgRemind==1){//短信提醒
            String sendmessage=SystemEnv.getHtmlLabelName(18910,language);
            String creater="";
            int creatertype=0;
            ArrayList smstemplist=new ArrayList();
            sql="select creater,creatertype,requestname from workflow_requestbase where requestid="+requestid;
            rs2.executeSql(sql);
            if(rs2.next()){
                creater=rs2.getString("creater");
                creatertype=Util.getIntValue(rs2.getString("creatertype"),0);
                if(remindtype == 0) { // 超时前提醒
                	sendmessage=SystemEnv.getHtmlLabelName(18015,language)+"("+rs2.getString("requestname")+")"+SystemEnv.getHtmlLabelName(18911,language);
                }else {
                	sendmessage=SystemEnv.getHtmlLabelName(18015,language)+"("+rs2.getString("requestname")+")已超时";
                }
            }
            SMSManager smsManager = new SMSManager();
            if(smsManager.isValid()){
            	// sq qc 409583 增加提醒方式
                for(String txr : txrs){
                	String recMobile = Util.null2String(resource.getMobile(""+txr));
                    if(!recMobile.isEmpty()){
                        if(smstemplist.indexOf(recMobile)<0){
                            smstemplist.add(recMobile);
                        }
                    }
                }
                if(isnodeoperator==1){//本节点操作人本人
                    //本节点操作人提醒
                    for(int k=0;k<userlist.size();k++){
                        int agenttype=Util.getIntValue((String)agenttypelist.get(k));
                        int agentorbyagentid=Util.getIntValue((String)agentorbyagentidlist.get(k));
                        userid=Util.getIntValue((String)userlist.get(k));
                        usertype=Util.getIntValue((String)usertypelist.get(k));
                        if(userid>0 && usertype==0){
                            String recMobile = resource.getMobile(""+userid);
                            if(recMobile !=null && !recMobile.trim().equals("")){
                                if(smstemplist.indexOf(recMobile)<0){
                                    smstemplist.add(recMobile);
                                    //smsManager.sendSMS(recMobile,sendmessage);
                                    if((","+remindusers).indexOf(","+userid+",")<0)
                                    remindusers+=userid+",";
                                }
                            }
                        }
                        //代理人提醒
                        if(agenttype>0 && agentorbyagentid>0){
                            String recMobile = resource.getMobile(""+agentorbyagentid);
                            if(recMobile !=null && !recMobile.trim().equals("")){
                                if(smstemplist.indexOf(recMobile)<0){
                                    smstemplist.add(recMobile);
                                    //smsManager.sendSMS(recMobile,sendmessage);
                                    if((","+remindusers).indexOf(","+agentorbyagentid+",")<0)
                                    remindusers+=agentorbyagentid+",";
                                }
                            }
                        }
                    }
                }
                if(iscreater==1 && creatertype==0){//创建人
                    String recMobile = resource.getMobile(""+creater);
                    if(recMobile !=null && !recMobile.trim().equals("")){
                        if(smstemplist.indexOf(recMobile)<0){
                            smstemplist.add(recMobile);
                            //smsManager.sendSMS(recMobile,sendmessage);
                            if((","+remindusers).indexOf(","+creater+",")<0)
                                remindusers+=creater+",";
                        }
                    }
                }
                if(ismanager==1){//本节点操作人经理
                    int managerid=0;
                    for(int k=0;k<userlist.size();k++){
                        int agenttype=Util.getIntValue((String)agenttypelist.get(k));
                        int agentorbyagentid=Util.getIntValue((String)agentorbyagentidlist.get(k));
                        userid=Util.getIntValue((String)userlist.get(k));
                        usertype=Util.getIntValue((String)usertypelist.get(k));
                        if(usertype==0){
                            managerid=Util.getIntValue(resource.getManagerID(userid+""),0);
                        }else{
                            managerid=Util.getIntValue(crminfo.getCustomerInfomanager(userid+""),0);
                        }
                        String recMobile = resource.getMobile(""+managerid);
                        if(recMobile !=null && !recMobile.trim().equals("")){
                            if(smstemplist.indexOf(recMobile)<0){
                                smstemplist.add(recMobile);
                                //smsManager.sendSMS(recMobile,sendmessage);
                                if((","+remindusers).indexOf(","+managerid+",")<0)
                                    remindusers+=managerid+",";
                            }
                        }
                    }
                }
                if(isother==1){//指定对象
                    ArrayList remindobjectlist=Util.TokenizerString(remindobjectids,",");
                    for(int i=0;i<remindobjectlist.size();i++){
                        String recMobile = resource.getMobile((String)remindobjectlist.get(i));
                        if(recMobile !=null && !recMobile.trim().equals("")){
                            if(smstemplist.indexOf(recMobile)<0){
                                smstemplist.add(recMobile);
                                //smsManager.sendSMS(recMobile,sendmessage);
                                if((","+remindusers).indexOf(","+remindobjectlist.get(i)+",")<0)
                                remindusers+=remindobjectlist.get(i)+",";
                            }
                        }
                    }
                }
                writeLog("短信提醒人：" + smstemplist.toArray());
                if(smstemplist.size()<1){
                    msgsend=true;
                }
                sendmessage = "0".equals(signPos) ? (sign + sendmessage) : (sendmessage + sign);
                for(int i=0;i<smstemplist.size();i++){
                    if(msgsend){
                        smsManager.sendSMS((String)smstemplist.get(i),sendmessage);
                    }else{
                        msgsend=smsManager.sendSMS((String)smstemplist.get(i),sendmessage);
                    }
                }
            }else{
                msgsend=true;
            }
        }
        /***********************************************************************************************/
        //微信提醒(QC:98106
        if(ChatsRemind==1){//微信提醒
        	System.out.println("进入超时提醒");
            String sendmessage=SystemEnv.getHtmlLabelName(18910,language);
            String creater="";
            int creatertype=0; 
            sql="select creater,creatertype,requestname from workflow_requestbase where requestid="+requestid;
            rs2.executeSql(sql);
            if(rs2.next()){
                creater=rs2.getString("creater");
                creatertype=Util.getIntValue(rs2.getString("creatertype"),0);
                sendmessage=SystemEnv.getHtmlLabelName(18015,language)+"("+rs2.getString("requestname")+")"+SystemEnv.getHtmlLabelName(18911,language);
            }
            WechatPropConfig wechatPropConfig = new WechatPropConfig();
            if(wechatPropConfig.isUseWechat()){
                if(isnodeoperator==1){//本节点操作人本人
                    //本节点操作人提醒
                    for(int k=0;k<userlist.size();k++){
                        int agenttype=Util.getIntValue((String)agenttypelist.get(k));
                        int agentorbyagentid=Util.getIntValue((String)agentorbyagentidlist.get(k));
                        userid=Util.getIntValue((String)userlist.get(k));
                        usertype=Util.getIntValue((String)usertypelist.get(k));
                        if(userid>0 && usertype==0){  
                                    if((","+remindusers).indexOf(","+userid+",")<0){
                                    remindusers+=userid+",";
                                } 
                        }
                        //代理人提醒





                        if(agenttype>0 && agentorbyagentid>0){ 
                                    if((","+remindusers).indexOf(","+agentorbyagentid+",")<0){
                                    remindusers+=agentorbyagentid+",";
                                }
                        }  
                    }
                }
                if(iscreater==1 && creatertype==0){//创建人 
                            if((","+remindusers).indexOf(","+creater+",")<0){
                                remindusers+=creater+","; 
                    }
                }
                if(ismanager==1){//本节点操作人经理
                    int managerid=0;
                    for(int k=0;k<userlist.size();k++){
                        int agenttype=Util.getIntValue((String)agenttypelist.get(k));
                        int agentorbyagentid=Util.getIntValue((String)agentorbyagentidlist.get(k));
                        userid=Util.getIntValue((String)userlist.get(k));
                        usertype=Util.getIntValue((String)usertypelist.get(k));
                        if(usertype==0){
                            managerid=Util.getIntValue(resource.getManagerID(userid+""),0);
                        }else{
                            managerid=Util.getIntValue(crminfo.getCustomerInfomanager(userid+""),0);
                        } 
						if(managerid > 0) {
                                if((","+remindusers).indexOf(","+managerid+",")<0){
                                    remindusers+=managerid+","; 
								}
                        }
                    }
                }
                if(isother==1){//指定对象
                    ArrayList remindobjectlist=Util.TokenizerString(remindobjectids,",");
                    for(int i=0;i<remindobjectlist.size();i++){ 
                                if((","+remindusers).indexOf(","+remindobjectlist.get(i)+",")<0){
                                remindusers+=remindobjectlist.get(i)+","; 
                        }
                    }
                }
                if(remindusers.length()>0){
                	chatssend=true;
                }
                writeLog("微信提醒人：" + remindusers);
               Map map= new HashMap();
		           map.put("detailid",requestid);
		           saveAndSendWechat.setHrmid(remindusers);
		           saveAndSendWechat.setMsg(sendmessage);
		           saveAndSendWechat.setMode(1);
		           saveAndSendWechat.setParams(map);
		           saveAndSendWechat.send(); 
            }else{
            	chatssend=true;
            }
        }
        //微信提醒(QC:98106
        /***********************************************************************************************/
        if(MailRemind==1){//邮件提醒
            String mailtoaddress="";
            String mailrequestname = SystemEnv.getHtmlLabelName(18910,language);
            String mailobject=SystemEnv.getHtmlLabelName(18910,language);
            String creater="";
            int creatertype=0;
            sql="select creater,creatertype,requestname from workflow_requestbase where requestid="+requestid;
            rs2.executeSql(sql);
            if(rs2.next()){
                creater=rs2.getString("creater");
                creatertype=Util.getIntValue(rs2.getString("creatertype"),0);
                if(remindtype == 0) { // 超时前提醒
                	// sq add 流程【标题】将超时
					mailrequestname =  rs2.getString("requestname")
							+ SystemEnv.getHtmlLabelName(18911, language);
                }else {
                	mailrequestname = rs2.getString("requestname")+"已超时";
                }
                // sq add 流程超时提醒【标题】
                mailobject=SystemEnv.getHtmlLabelName(18910,language)+"("+rs2.getString("requestname")+")";
            }
            // sq qc 409583 增加提醒方式
            for(String txr : txrs){
            	String tempmail = Util.null2String(resource.getEmail(""+txr));
                if(!tempmail.isEmpty()){
                    if((","+mailtoaddress).indexOf(","+tempmail+",")<0){
                    	 mailtoaddress+=tempmail+",";
                    }
                }
            }
            if(isnodeoperator==1){//本节点操作人本人
                //本节点操作人提醒
                for(int k=0;k<userlist.size();k++){
                    int agenttype=Util.getIntValue((String)agenttypelist.get(k));
                    int agentorbyagentid=Util.getIntValue((String)agentorbyagentidlist.get(k));
                    userid=Util.getIntValue((String)userlist.get(k));
                    usertype=Util.getIntValue((String)usertypelist.get(k));
                    if(userid>0){
                        if(usertype==0){
                            String tempmail=resource.getEmail(""+userid);
                            if(tempmail!=null && !tempmail.trim().equals("")){
                                if((","+mailtoaddress).indexOf(","+tempmail+",")<0){
                                    mailtoaddress+=tempmail+",";
                                    if((","+remindusers).indexOf(","+userid+",")<0)
                                    remindusers+=userid+",";
                                }
                            }
                        }else{ //客户
                            String tempmail=crminfo.getCustomerInfoEmail(""+userid);
                            if(tempmail!=null && !tempmail.trim().equals("")){
                                if((","+mailtoaddress).indexOf(","+tempmail+",")<0){
                                    mailtoaddress+=tempmail+",";
                                    if((","+remindusers).indexOf(","+userid+",")<0)
                                    remindusers+=userid+",";
                                }
                            }
                        }
                    }
                    //代理人提醒





                    if(agenttype>0 && agentorbyagentid>0){
                        if(usertype==0){
                            String tempmail=resource.getEmail(""+agentorbyagentid);
                            if(tempmail!=null && !tempmail.trim().equals("")){
                                if((","+mailtoaddress).indexOf(","+tempmail+",")<0){
                                    mailtoaddress+=tempmail+",";
                                    if((","+remindusers).indexOf(","+agentorbyagentid+",")<0)
                                    remindusers+=agentorbyagentid+",";
                                }
                            }
                        }else{//客户
                            String tempmail=crminfo.getCustomerInfoEmail(""+agentorbyagentid);
                            if(tempmail!=null && !tempmail.trim().equals("")){
                                if((","+mailtoaddress).indexOf(","+tempmail+",")<0){
                                    mailtoaddress+=tempmail+",";
                                    if((","+remindusers).indexOf(","+agentorbyagentid+",")<0)
                                    remindusers+=agentorbyagentid+",";
                                }
                            }
                        }
                    }
                }
            }
            if(iscreater==1){//创建人





                if(creatertype==0){
                    String tempmail=resource.getEmail(creater);
                    if(tempmail!=null && !tempmail.trim().equals("")){
                        if((","+mailtoaddress).indexOf(","+tempmail+",")<0){
                            mailtoaddress+=tempmail+",";
                            if((","+remindusers).indexOf(","+creater+",")<0)
                                remindusers+=creater+",";
                        }
                    }
                }else{//客户
                    String tempmail=crminfo.getCustomerInfoEmail(creater);
                    if(tempmail!=null && !tempmail.trim().equals("")){
                        if((","+mailtoaddress).indexOf(","+tempmail+",")<0){
                            mailtoaddress+=tempmail+",";
                            if((","+remindusers).indexOf(","+creater+",")<0)
                                remindusers+=creater+",";
                        }
                    }
                }
            }
            if(ismanager==1){//本节点操作人经理
                int managerid=0;
                for(int k=0;k<userlist.size();k++){
                    int agenttype=Util.getIntValue((String)agenttypelist.get(k));
                    int agentorbyagentid=Util.getIntValue((String)agentorbyagentidlist.get(k));
                    userid=Util.getIntValue((String)userlist.get(k));
                    usertype=Util.getIntValue((String)usertypelist.get(k));
                    if(usertype==0){
                        managerid=Util.getIntValue(resource.getManagerID(userid+""),0);
                    }else{
                        managerid=Util.getIntValue(crminfo.getCustomerInfomanager(userid+""),0);
                    }
                    if(managerid>0){
                        String tempmail=resource.getEmail(""+managerid);
                        if(tempmail!=null && !tempmail.trim().equals("")){
                            if((","+mailtoaddress).indexOf(","+tempmail+",")<0){
                                mailtoaddress+=tempmail+",";
                                if((","+remindusers).indexOf(","+managerid+",")<0)
                                    remindusers+=managerid+",";
                            }
                        }
                    }
                }
            }
            if(isother==1){//指定对象
                ArrayList remindobjectlist=Util.TokenizerString(remindobjectids,",");
                for(int i=0;i<remindobjectlist.size();i++){
                    String tempmail=resource.getEmail((String)remindobjectlist.get(i));
                    if(tempmail!=null && !tempmail.trim().equals("")){
                        if((","+mailtoaddress).indexOf(","+tempmail+",")<0){
                            mailtoaddress+=tempmail+",";
                            if((","+remindusers).indexOf(","+remindobjectlist.get(i)+",")<0)
                                remindusers+=remindobjectlist.get(i)+",";
                        }
                    }
                }
            }
            //System.out.println("sq~~~~~~~:mailtoaddress :" + mailtoaddress);
            if(!"".equals(mailtoaddress)){
                    mailtoaddress = mailtoaddress.substring(0,mailtoaddress.length()-1);
                    SendMail sm = new SendMail();
                    SystemComInfo systemComInfo = new SystemComInfo();
                    String defmailserver = systemComInfo.getDefmailserver();
                    String defneedauth = systemComInfo.getDefneedauth();
                    String defmailuser = systemComInfo.getDefmailuser();
                    String defmailpassword = systemComInfo.getDefmailpassword();
                    String defmailfrom=systemComInfo.getDefmailfrom();
                    sm.setMailServer(defmailserver);
                    if (defneedauth.equals("1")) {
                    sm.setNeedauthsend(true);
                    sm.setUsername(defmailuser);
                    sm.setPassword(defmailpassword);
                    } else{
                    sm.setNeedauthsend(false);
                    }
                    /*try {
                          mailrequestname = new String(mailrequestname.getBytes("UTF-8"),"iso-8859-1");
                    } catch (UnsupportedEncodingException e){
                        e.printStackTrace();
                    }*/
                    // sq add 邮件正文调整
                    RecordSet sqrs = new RecordSet();
                    sqrs.executeQuery("select userid,receivedate,receivetime from workflow_currentoperator where requestid=? and isremark=0", requestid);
                    String dbr = "";	// 代办人
                    String receivedate = "";
                    String receivetime = "";
                    String receive = "";
                    Calendar jssj = null;	// 接受时间
                    DepartmentComInfo dci = new DepartmentComInfo();
                    while(sqrs.next()){
                    	receivedate = sqrs.getString(2);
                        receivetime = sqrs.getString(3);
                        receive = receivedate + " " + receivetime;
                        jssj = TimeUtil.getCalendar(receive);	// 接受时间
                        String _dbr = sqrs.getString(1);
                        //writeLog("代办人：" + _dbr +"," + resource.getLastname(_dbr) + "," + dci.getDepartmentname(resource.getDepartmentID(_dbr)));
                    	dbr+= "," + resource.getLastname(_dbr) + "(" + dci.getDepartmentname(resource.getDepartmentID(_dbr)) + ")";
                    }
                    dbr = dbr.length() == 0 ? "" : dbr.substring(1);
                    mailrequestname = "<br/>流程：" + mailrequestname;
                    mailrequestname +="<br/>当前节点操作者：" + dbr ;
                    // nodeid=286	selectnodepass 超时时间来源
                    sqrs.executeQuery("select * from workflow_nodelink where nodeid=? and (selectnodepass<>'' or selectnodepass is not null)", nodeid);
                    sqrs.next();
                    //selectnodepass =1为指定小时分钟  =2为表单超时字段。
                    int selectnodepass = Util.getIntValue(sqrs.getString("selectnodepass"));
                    // 要求处理时间
                    int hour = Util.getIntValue(sqrs.getString("nodepasshour"));
                    int mins = Util.getIntValue(sqrs.getString("nodepassminute"));
                    String rq = Util.null2String(sqrs.getString("dateField"));
                    String sj = Util.null2String(sqrs.getString("timeField"));
                    String wfid = sqrs.getString("workflowid");
                    String yqclsj = "";
                    if(selectnodepass == 1){
                    	jssj.add(Calendar.HOUR, hour);
                    	jssj.add(Calendar.MINUTE, mins);
                    	yqclsj = TimeUtil.getFormartString(jssj, "yyyy-MM-dd HH:mm:ss");
                    }else{
                    	sqrs.executeQuery("SELECT * FROM workflow_base WHERE ID=?", wfid);
                    	sqrs.next();
                    	int formid = Util.getIntValue(rs.getString("formid"));
                    	if(formid<0){
                    		formid = formid * (-1);
                    	}
                    	sqrs.executeQuery("select * from formtable_main_" +formid + " where requestid=?" , requestid);
                    	sqrs.next();
                    	String set_rq = Util.null2String(sqrs.getString(rq));
                    	String set_sj = Util.null2String(sqrs.getString(sj));
                    	yqclsj = set_rq + " " + set_sj+":00";
                    }
                    mailrequestname += "<br/>目标处理时间：" + yqclsj;
                    int cs_day = 0,cs_xs=0,cs_fz=0;	// 超时天、小时、分钟
                    Calendar c_yqsj = TimeUtil.getCalendar(yqclsj);	// 日历格式要求处理时间
                    Calendar c_cur = Calendar.getInstance();
                    long nd = 24 * 60 * 60;
            	    long nh = 60 * 60;
            	    long nm = 60;
            		long diff = (c_cur.getTimeInMillis() - c_yqsj.getTimeInMillis()) /1000;
            		long t = diff / nd;	// 超时天
            		long s = diff % (nd) / nh;	// 超时小时
            		long m = diff % nd % nh / nm;	// 超时分钟
                    mailrequestname += "<br/>超时时间：" + t + "天" + s + "小时" + m + "分钟";
                    System.out.println("mailrequestname4: " + mailrequestname);
                    writeLog("邮件提醒人：" + mailtoaddress);
                    mailsend=sm.sendhtml(defmailfrom, mailtoaddress, null, null,mailobject , mailrequestname, 3, "3");
            }
            
        }
        
        if(InfoCentreRemind==1){ // 工作流提醒
        	System.out.println("InfoCentreRemind:"+InfoCentreRemind+"  CustomWorkflowid:"+CustomWorkflowid);
        	if(CustomWorkflowid>0){
            	String requestname="";
                sql="select t.requestid,t.requestname,t.requestlevel,t1.userid  from workflow_currentoperator t1,workflow_requestbase t where t1.requestid=t.requestid and t.requestid="+requestid+" and t1.nodeid="+nodeid+" and isremark='0' ";
                rs8.executeSql(sql);
                if(rs8.next()){
                	requestname=Util.null2String(rs8.getString("requestname"));
                	hashMap.put("requestname", requestname);
					String remark = "相关流程：<a href='/workflow/request/ViewRequest.jsp?requestid="+requestid+"&isovertime=0'>"+requestname+"</a>";
					if(remindtype == 0) { // 超时前提醒
						requestname = " 流程{"+requestname+"}将超时";
					}else {
						requestname = " 流程{"+requestname+"}已超时";
					}
					hashMap.put("requestlevel", Util.null2String(rs8.getString("requestlevel")));
					
					String csr = ""; // 超时人
					for(int k=0;k<userlist.size();k++){
	                    userid=Util.getIntValue((String)userlist.get(k));
	                    usertype=Util.getIntValue((String)usertypelist.get(k));
	                    if(usertype==0 && userid>0){
	                    	if((csr + ",").indexOf(","+userid+",")<0){
	                    		csr += "," + userid;
	                        }
	                    }
	                }

		            if(!"".equals(csr)) {
		            	csr = csr.substring(1);
		            }
		            hashMap.put("csr", csr); // 超时人
		            
					String txr = ""; // 提醒人
					if(isnodeoperator==1){//本节点操作人本人
		                for(int k=0;k<userlist.size();k++){
		                    int agenttype=Util.getIntValue((String)agenttypelist.get(k));
		                    int agentorbyagentid=Util.getIntValue((String)agentorbyagentidlist.get(k));
		                    userid=Util.getIntValue((String)userlist.get(k));
		                    usertype=Util.getIntValue((String)usertypelist.get(k));
		                    if(usertype == 0) {
		                    //本节点操作人提醒
		                    if(userid>0){
		                        if((txr + ",").indexOf(","+userid+",")<0){
		                        	txr += "," + userid;
		                        }
		                    }
		                    //代理人提醒
		                    if(agenttype>0 && agentorbyagentid>0){
		                        if((txr + ",").indexOf(","+agentorbyagentid+",")<0){
		                        	txr += "," + agentorbyagentid;
		                        }
		                    }
		                    }
		                }
		            }
		            if(iscreater==1){//创建人
		                sql="select creater,creatertype from workflow_requestbase where requestid="+requestid;
		                rs2.executeSql(sql);
		                if(rs2.next()){
		                	if(!"1".equals(rs2.getString("creatertype"))) {
		                		int creatertmp=Util.getIntValue(rs2.getString("creater"),0);
		                		if((txr + ",").indexOf(","+creatertmp+",")<0){
		                			txr += "," + creatertmp;
		                		}
		                	}
		                }
		            }
		            if(ismanager==1){//本节点操作人经理
		                int managerid=0;
		                for(int k=0;k<userlist.size();k++){
		                    userid=Util.getIntValue((String)userlist.get(k));
		                    usertype=Util.getIntValue((String)usertypelist.get(k));
		                    if(usertype==0){
		                        managerid=Util.getIntValue(resource.getManagerID(userid+""),0);
		                    }
		                    if(managerid>0){
		                        if((txr + ",").indexOf(","+managerid+",")<0){
		                        	txr += "," + managerid;
		                        }
		                    }
		                }
		            }
		            if(isother==1){//指定对象
		                ArrayList remindobjectlist=Util.TokenizerString(remindobjectids,",");
		                for(int i=0;i<remindobjectlist.size();i++){
		                    int tempid=Util.getIntValue((String)remindobjectlist.get(i));
		                    if((txr + ",").indexOf(","+tempid+",")<0){
		                    	txr += "," + tempid;
		                    }
		                }
		            }
		         // sq 流程提醒人不包含增加的提醒人处理
		            
		            for(String t : txrs){
		            	if(!txr.contains(t)){
	            			txr += "," + t;
		            	}
		            }
		            writeLog("工作流提醒人：" + txr);
		            if(!"".equals(txr)) {
		            	txr = txr.substring(1);
		            }
		            hashMap.put("txr", txr); // 提醒人
		            
					docreateWorkflow(CustomWorkflowid+"","1",requestname,remark, hashMap);
					wfcreate = true;
               }
        	}
        }
        //微信提醒(QC:98106)
        if(remindusers.length()>0 && ((FlowRemind==1 && wfsend) || (MsgRemind==1 && msgsend) || (MailRemind==1 && mailsend)) || (InfoCentreRemind==1 && wfcreate)|| (ChatsRemind==1 && chatssend)){
        	if(remindtype == 0) { // 超时前提醒
        		sql="update workflow_currentoperator set isreminded='1', isreminded_csh = NULL, lastRemindDatetime='" + nowdatetime + "' where isremark='0' and requestid="+requestid;
        	}else {
        		sql="update workflow_currentoperator set isreminded_csh='1', lastRemindDatetime='" + nowdatetime + "' where isremark='0' and requestid="+requestid;
        	}
            rs2.executeSql(sql);
            log.debug(sql);
        }
    }
    
    public String docreateWorkflow(String workflowid,String createuser,String requestname,String remark, HashMap hashMap){
    	WorkflowService  wfimp=new 	WorkflowServiceImpl();
		//创建流程接口调用方式
		WorkflowRequestInfo info=new WorkflowRequestInfo();
		int userId=1;
		info.setRequestName(requestname);//此处可以自定义标题名称





		info.setCreatorId(createuser);//创建人





		info.setRemark(remark);
		WorkflowBaseInfo base=new WorkflowBaseInfo();

		RecordSet rs = new RecordSet();
	    base.setWorkflowId(workflowid);//流程id
		info.setWorkflowBaseInfo(base);
		WorkflowMainTableInfo maininfo=new WorkflowMainTableInfo();
		WorkflowRequestTableRecord[] tablre = new WorkflowRequestTableRecord[1]; 
		tablre[0]=new WorkflowRequestTableRecord();

		int wfid = (Integer) hashMap.get("workflowid");
		int formid = 0;
	    String isbill = "";
	    rs.executeSql("select * from workflow_base where id=" + wfid);
	    if(rs.next()) {
	    	formid = Util.getIntValue(Util.null2String(rs.getString("formid")), 0);
	    	isbill = Util.null2String(rs.getString("isbill"));
	    }
	    
	    // 获取取值字段信息
	    int requestid = (Integer) hashMap.get("requestid");
	    HashMap fromFieldValueMap = new HashMap();
	    HashMap<String, HashMap> fromFieldMap = new HashMap();
	    HashMap fromFieldViewTypeMap = new HashMap();
	    String tablename = "workflow_form";
	    if("1".equals(isbill)) {
	    	String detailkeyfield = "";
	    	rs.executeSql("select * from workflow_bill where id=" + formid);
	    	if(rs.next()) {
		    	tablename = Util.null2String(rs.getString("tablename"));
		    	detailkeyfield = Util.null2String(rs.getString("detailkeyfield"));
	    	}
	    	rs.executeSql("select * from workflow_billfield where billid=" + formid + " order by viewtype, detailtable, dsporder");
	    	while(rs.next()) {
	    		int fieldid = Util.getIntValue(Util.null2String(rs.getString("id")), 0);
	    		if(fieldid <= 0) {
	    			continue;
	    		}
	    		int viewtype = Util.getIntValue(Util.null2String(rs.getString("viewtype")), 0);
	    		String detailtable = viewtype == 0 ? tablename : Util.null2String(rs.getString("detailtable"));
	    		
	    		HashMap<Integer, String> map = fromFieldMap.get(detailtable) == null ? new HashMap() : (HashMap) fromFieldMap.get(detailtable);
	    		map.put(fieldid, Util.null2String(rs.getString("fieldname")));
	    		fromFieldMap.put(detailtable, map);
	    		fromFieldViewTypeMap.put(fieldid, viewtype);
	    	}
	    	
	    	String detailkeyfieldvalue = "";
	    	HashMap<Integer, String> mainmap = fromFieldMap.get(tablename);
	    	rs.executeSql("select * from " + tablename + " where requestid=" + requestid);
	    	if(rs.next()) {
	    		detailkeyfieldvalue = Util.null2String(rs.getString("id"));
	    		for(Map.Entry<Integer, String> _entry : mainmap.entrySet()) {
	    			String fieldname = _entry.getValue();
	    	    	int fieldid = (int) _entry.getKey();
	    	    	ArrayList list = fromFieldValueMap.get(fieldid) == null ? new ArrayList() : (ArrayList) fromFieldValueMap.get(fieldid);
	    	    	list.add(Util.null2String(rs.getString(fieldname)));
	    	    	fromFieldValueMap.put(fieldid, list);
	    	    }
	    	}
	    	
	    	for(Map.Entry<String, HashMap> entry : fromFieldMap.entrySet()) {
		    	String key = entry.getKey();
		    	if(key.equals(tablename)) {
		    		continue;
		    	}
		    	HashMap<Integer, String> _map = entry.getValue();
		    	String sql = "select * from " + key + " where " + detailkeyfield + "=" + detailkeyfieldvalue + " order by id";
		    	rs.executeSql(sql);
		    	while(rs.next()) {
		    		for(Map.Entry<Integer, String> _entry : _map.entrySet()) {
		    			String fieldname = _entry.getValue();
		    	    	int fieldid = (int) _entry.getKey();
		    	    	ArrayList list = fromFieldValueMap.get(fieldid) == null ? new ArrayList() : (ArrayList) fromFieldValueMap.get(fieldid);
		    	    	list.add(Util.null2String(rs.getString(fieldname)));
		    	    	fromFieldValueMap.put(fieldid, list);
		    	    }
		    	}
		    }
	    }else {
	    	HashMap<Integer, String> map = new HashMap();
	    	rs.executeSql("select a.* from workflow_formdict a, workflow_formfield b where a.id = b.fieldid and b.formid=" + formid + " order by b.fieldorder, a.id");
	    	while(rs.next()) {
	    		int fieldid = Util.getIntValue(Util.null2String(rs.getString("id")), 0);
	    		if(fieldid <= 0) {
	    			continue;
	    		}
	    		map.put(fieldid, Util.null2String(rs.getString("fieldname")));
	    		fromFieldViewTypeMap.put(fieldid, 0);
	    	}
	    	fromFieldMap.put(tablename, map);
	    	
	    	String detailtable = "workflow_formdetail";
	    	HashMap<Integer, String> detailmap = new HashMap();
	    	rs.executeSql("select a.* from workflow_formdictdetail a, workflow_formfield b where a.id = b.fieldid and b.formid=" + formid + " order by b.fieldorder, a.id");
	    	while(rs.next()) {
	    		int fieldid = Util.getIntValue(Util.null2String(rs.getString("id")), 0);
	    		if(fieldid <= 0) {
	    			continue;
	    		}
	    		detailmap.put(fieldid, Util.null2String(rs.getString("fieldname")));
	    		fromFieldViewTypeMap.put(fieldid, 1);
	    	}
	    	fromFieldMap.put(detailtable, detailmap);
	    	
	    	for(Map.Entry<String, HashMap> entry : fromFieldMap.entrySet()) {
		    	HashMap<Integer, String> _map = entry.getValue();
		    	String key = entry.getKey();
		    	String sql = "select * from " + key + " where requestid=" + requestid;
		    	if(!"workflow_form".equals(key)) {
		    		sql += " order by id";
		    	}
		    	rs.executeSql(sql);
		    	while(rs.next()) {
		    		for(Map.Entry<Integer, String> _entry : _map.entrySet()) {
		    			String fieldname = _entry.getValue();
		    	    	int fieldid = (int) _entry.getKey();
		    	    	ArrayList list = fromFieldValueMap.get(fieldid) == null ? new ArrayList() : (ArrayList) fromFieldValueMap.get(fieldid);
		    	    	list.add(Util.null2String(rs.getString(fieldname)));
		    	    	fromFieldValueMap.put(fieldid, list);
		    	    }
		    	}
		    }
	    	
	    }
	    for(int i = -1; i >= -6; i--) { // 系统字段
	    	ArrayList list = new ArrayList();
	    	if(i == -1) { // 流程ID
	    		list.add("" + requestid);
			}else if(i == -2) { // 流程标题
				list.add(Util.null2String(hashMap.get("requestname")));
			}else if(i == -3) { // 紧急程度
				list.add(Util.null2String(hashMap.get("requestlevel")));
			}else if(i == -4) { // 提醒人
				list.add(Util.null2String(hashMap.get("txr")));
			}else if(i == -5) { // 超时人
				list.add(Util.null2String(hashMap.get("csr")));
			}else if(i == -6) { // 超时节点
				list.add(Util.null2String(hashMap.get("nodeid")));
			}
	    	fromFieldValueMap.put(i, list);
	    	fromFieldViewTypeMap.put(i, 0);
	    }
	    
	    int maxGroupid = -1;
	    HashMap toFieldGroupMap = new HashMap();
		int id = (Integer) hashMap.get("id");
		rs.executeSql("select * from workflow_nodelinkOTField where overTimeId=" + id + " order by toFieldGroupid, id");
		while(rs.next()) {
			int toFieldId = Util.getIntValue(Util.null2String(rs.getString("toFieldId")), 0);
			if(toFieldId == 0) {
				continue;
			}
			int toFieldGroupid = Util.getIntValue(Util.null2String(rs.getString("toFieldGroupid")), 0);
			if(toFieldGroupid > maxGroupid) {
				maxGroupid = toFieldGroupid;
			}
			HashMap map = new HashMap();
			map.put("toFieldId", toFieldId);
			map.put("toFieldName", Util.null2String(rs.getString("toFieldName")));
			map.put("toFieldGroupid", toFieldGroupid);
			map.put("fromFieldId", Util.getIntValue(Util.null2String(rs.getString("fromFieldId")), 0));
			
			ArrayList list = toFieldGroupMap.get(toFieldGroupid) == null ? new ArrayList() : (ArrayList) toFieldGroupMap.get(toFieldGroupid);
			list.add(map);
			toFieldGroupMap.put(toFieldGroupid, list);
		}
		
		if(toFieldGroupMap.get(-1) != null) { // 系统字段
			ArrayList sysTableFields = (ArrayList) toFieldGroupMap.get(-1);
			for(int i = 0; i < sysTableFields.size(); i++) {
				HashMap map = (HashMap) sysTableFields.get(i);
				int toFieldId = (Integer) map.get("toFieldId");
				int fromFieldId = (Integer) map.get("fromFieldId");
				ArrayList list = fromFieldValueMap.get(fromFieldId) == null ? new ArrayList() : (ArrayList) fromFieldValueMap.get(fromFieldId);
				String fromFieldValue = "";
				try {
					fromFieldValue = (String) list.get(0);
				} catch (Exception e) {
				}
				if(toFieldId == -1) { // 流程标题
					info.setRequestName(fromFieldValue);
				}else if(toFieldId == -2) { // 紧急程度
					info.setRequestLevel(fromFieldValue);
				}
			}
		}
		
		// 主表数据
		ArrayList mainTableFields = toFieldGroupMap.get(0) == null ? new ArrayList() : (ArrayList) toFieldGroupMap.get(0); // 主表字段信息
		WorkflowRequestTableField[] wrti = new WorkflowRequestTableField[mainTableFields.size()];
		for(int i = 0; i < mainTableFields.size(); i++) {
			HashMap map = (HashMap) mainTableFields.get(i);
			int fromFieldId = (Integer) map.get("fromFieldId");
			ArrayList list = fromFieldValueMap.get(fromFieldId) == null ? new ArrayList() : (ArrayList) fromFieldValueMap.get(fromFieldId);
			String fromFieldValue = "";
			try {
				fromFieldValue = (String) list.get(0);
			} catch (Exception e) {
			}
			
			wrti[i] = new WorkflowRequestTableField();
			wrti[i].setFieldName((String) map.get("toFieldName"));
			wrti[i].setFieldValue(fromFieldValue); // 字段的值
			wrti[i].setView(true); // 字段是否可见
			wrti[i].setEdit(true); // 字段是否可编辑
		}
									
		tablre[0].setWorkflowRequestTableFields(wrti);
		maininfo.setRequestRecords(tablre);
		info.setWorkflowMainTableInfo(maininfo);
		
		// 明细数据
		if(maxGroupid > 0) {
			WorkflowDetailTableInfo wdti[] = new WorkflowDetailTableInfo[maxGroupid];
			for(int i = 0; i < maxGroupid; i++) {
				ArrayList detailTableFields = toFieldGroupMap.get(i + 1) == null ? new ArrayList() : (ArrayList) toFieldGroupMap.get(i + 1); // 明细表字段信息
				
				WorkflowRequestTableRecord[] wrtr = null;
				int count = 0; // 明细表数据总行数
				int n = 0; // 明细表数据当前行
				do {
					WorkflowRequestTableField[] wrtf = new WorkflowRequestTableField[detailTableFields.size()];
					for(int j = 0; j < detailTableFields.size(); j++) {
						HashMap map = (HashMap) detailTableFields.get(j);
						int fromFieldId = (Integer) map.get("fromFieldId");
						int fromFieldViewType = Util.getIntValue(Util.null2String(fromFieldViewTypeMap.get(fromFieldId)), 0);
						ArrayList list = fromFieldValueMap.get(fromFieldId) == null ? new ArrayList() : (ArrayList) fromFieldValueMap.get(fromFieldId);
						if(n == 0 && count < list.size()) {
							count = list.size();
						}
						String fromFieldValue = "";
						try {
							if(fromFieldViewType == 0) {
								fromFieldValue = (String) list.get(0);
							}else {
								fromFieldValue = (String) list.get(n);
							}
						} catch (Exception e) {
						}
						
						wrtf[j] = new WorkflowRequestTableField();
						wrtf[j].setFieldName((String) map.get("toFieldName"));
						wrtf[j].setFieldValue(fromFieldValue); // 字段的值
						wrtf[j].setView(true); // 字段是否可见
						wrtf[j].setEdit(true); // 字段是否可编辑
					}
					if(count > 0) {
						if(n == 0) {
							wrtr = new WorkflowRequestTableRecord[count]; // 共count行数据
						}
						wrtr[n] = new WorkflowRequestTableRecord();
						wrtr[n].setWorkflowRequestTableFields(wrtf);
						//wrtr[n - 1].setRecordOrder(n);
					}
					n++;
				}while(n < count);
				
				wdti[i] = new WorkflowDetailTableInfo();
				wdti[i].setWorkflowRequestTableRecords(wrtr); // 加入明细表的数据
			}
			info.setWorkflowDetailTableInfos(wdti);
		}
		
	    String flags=wfimp.doCreateWorkflowRequest(info, userId);
	    
	    this.writeLog("docreateWorkflow##workflowid:"+workflowid+" requestname:"+requestname+" remark:"+remark);
		return flags;
    }

	private String getDateValue(int requestid, String dateField, String timeField) {
		String sql;
		String dateValue = "";
		String timeValue = "";
		//通过requestid查找formid
		sql="select wr.requestid,wr.workflowid,wb.id,wb.formid,wb.workflowname,wb.isbill from workflow_requestbase wr,workflow_base wb where wr.workflowid=wb.id and wr.requestid="+requestid;
		rs2.executeSql(sql);
		rs2.next();
		String formid = Util.null2String(rs2.getString("formid"));
		String isbill = Util.null2String(rs2.getString("isbill"));
		String fieldstr = "";
		if(!"".equals(timeField)){
			fieldstr = dateField+","+timeField;
		}else{
			fieldstr = dateField;
		}
		if("1".equals(isbill)){
			//通过formid,字段名 获取tablename
			sql="select b.tablename from workflow_bill b where b.id="+formid+" ";
			rs2.executeSql(sql);
			rs2.next();
			String tablename = Util.null2String(rs2.getString("tablename"));
			//通过requestid 在tablename中找到dateField的值





			sql="select "+fieldstr+" from "+tablename+" where requestid="+requestid;
		}else{
			sql = "select "+fieldstr+" from workflow_form where requestid="+requestid;
		}
		
		rs2.executeSql(sql);
		if(rs2.next()){
			dateValue = Util.null2String(rs2.getString(dateField));
			if(!"".equals(timeField)){
				timeValue = Util.null2String(rs2.getString(timeField));
			}
		}
		if(!dateValue.equals("")){
			if("".equals(timeValue)) dateValue += " 00:00:00";
	    	else dateValue += " "+timeValue+":00";
		}
		return dateValue;
	}

    /**
     * 检查是否有必填项





     * @param requestid
     * @param workflowid
     * @param nodeid
     * @return boolean
     */
    private boolean hasNeedInputField(int requestid,int workflowid,int nodeid){
    	List lstDetailFields = null;
		Map<String, String> mainTblFields = null;
        try {
			WorkflowComInfo workflowTool = new WorkflowComInfo();
			int isBill = Util.getIntValue(workflowTool.getIsBill(String.valueOf(workflowid)));
			int formID = Util.getIntValue(workflowTool.getFormId(String.valueOf(workflowid)));
			FieldInfo fieldTool = new FieldInfo();
			fieldTool.setRequestid(requestid);
			fieldTool.GetManTableField(formID, isBill, 7);
			fieldTool.GetDetailTblFields(formID, isBill, 7);
			
			mainTblFields = fieldTool.getMainFieldValues();
			lstDetailFields = fieldTool.getDetailFieldValues();
		} catch (Exception e) {
			log.info("Catch a exception during instantiate the WorkflowComInfo.", e);
			return true;
		}
        
        String sql="select a.ismode,a.showdes,b.formid,b.isbill from workflow_flownode a,workflow_base b where a.workflowid=b.id and a.workflowid="+workflowid+" and a.nodeid="+nodeid;
        rs3.executeSql(sql);
        if(rs3.next()){
            int ismode=Util.getIntValue(rs3.getString("ismode"),0);
            int showdes=Util.getIntValue(rs3.getString("showdes"),0);
            if(ismode==0 || (ismode==1 && showdes==1)){//一般模式





                sql="select fieldid from workflow_nodeform where ismandatory='1' and nodeid="+nodeid;
            }
			//add by liao dong update
            else if(ismode == 2){ //html模式
            	 sql="select fieldid from workflow_nodeform where ismandatory='1' and nodeid="+nodeid;
            }
            //end 	
					
			else{//模板模式
                sql="select fieldid from workflow_modeview where ismandatory='1' and nodeid="+nodeid;
            }
            rs3.executeSql(sql);
            while (rs3.next()) {
                String fieldid=rs3.getString("fieldid");
                //是否为主表单字段
                if(mainTblFields.containsKey(fieldid)){
                	String fieldValue = mainTblFields.get(fieldid);
                	if(fieldValue == null || "".equals(fieldValue)){
                		return true;
                	}
                }else{
                	//主表单中不存在该字段，则对多明细字段进行循环。





                	for (int i = 0; i < lstDetailFields.size(); i++) {
						List tmpList = (List)lstDetailFields.get(i);
						for (int j = 0; j < tmpList.size(); j++) {
							Map<String, String> mapDetailFields = (Map<String, String>)tmpList.get(j);
							if(!mapDetailFields.containsKey(fieldid)){
								break;
							}else{
								String fieldValue = mapDetailFields.get(fieldid);
			                	if(fieldValue == null || "".equals(fieldValue)){
			                		return true;
			                	}
							}
						}
					}
                }
            }
            
        }
        return false;
    }

    /**
     * 获得下一节点的出口id
     * @param requestid
     * @param nodeid
     * @param userid
     * @param usertype
     * @return linkid 出口id
     */
    public int getNextNode(int requestid,int nodeid,int userid,int usertype){
        // 查询当前请求的一些基本信息





        rs3.executeProc("workflow_Requestbase_SByID", requestid + "");
        int creater=0;
        int creatertype=0;
        int workflowid=0;
        int isbill=0;
        int formid=0;
        int billid=0;
        String billtablename="";
        String nodetype="";
        if(rs3.next()){
            creater = Util.getIntValue(rs3.getString("creater"), 0);
            creatertype = Util.getIntValue(rs3.getString("creatertype"), 0);
            workflowid= Util.getIntValue(rs3.getString("workflowid"), 0);
        }
        rs3.executeSql("select isbill,formid from workflow_base where id="+workflowid);
        if(rs3.next()){
            isbill=Util.getIntValue(rs3.getString("isbill"),0);
            formid=Util.getIntValue(rs3.getString("formid"),0);
        }
        rs3.executeSql("select nodetype from workflow_flownode where workflowid="+workflowid+" and nodeid="+nodeid);
        if(rs3.next()){
            nodetype=rs3.getString("nodetype");
        }
        rs3.executeSql("select tablename from workflow_bill where id="+formid);
        if(rs3.next()){
            billtablename=Util.null2String(rs3.getString("tablename"));
        }
        if(!billtablename.trim().equals("")){
            rs3.executeSql("select id from "+billtablename+" where requestid="+requestid);
            if(rs3.next()){
                billid=Util.getIntValue(rs3.getString("id"));
            }
        }
        
        int mgrID = this.updateManagerField(requestid, formid, isbill, userid);
        
        boolean hasnextnodeoperator =false;
        RequestNodeFlow requestNodeFlow = new RequestNodeFlow();
        requestNodeFlow.setRequestid(requestid);
        requestNodeFlow.setNodeid(nodeid);
        requestNodeFlow.setNodetype(nodetype);
        requestNodeFlow.setWorkflowid(workflowid);
        requestNodeFlow.setUserid(userid);
		requestNodeFlow.setIsreject(0);
        requestNodeFlow.setUsertype(usertype);
        requestNodeFlow.setCreaterid(creater);
        requestNodeFlow.setCreatertype(creatertype);
        requestNodeFlow.setIsbill(isbill);
        requestNodeFlow.setBillid(billid);
        requestNodeFlow.setFormid(formid);
        requestNodeFlow.setBilltablename(billtablename);
        requestNodeFlow.setRecordSet(rs3);
		requestNodeFlow.setIsGetFlowCodeStr(false);//超时不获取流程编号





        hasnextnodeoperator = requestNodeFlow.getNextNode();
        //还原Manager
        this.rollbackUpdatedManagerField(requestid, formid, isbill, mgrID);
        if(hasnextnodeoperator){
            return requestNodeFlow.getNextLinkid();
        }else{
            return 0;
        }
    }

    /**
     * 指定一个流程流转到下一节点
     * @param requestid
     */
    public void autoFLowNextNode(int requestid) {
    	autoFLowNextNode(requestid, "");
    }
    
    /**
     * 增加指定节点范围可自动流转至下一节点，用于分叉情况
     */
    public void autoFLowNextNode(int requestid, String appointNodeScope) {
        RecordSet rs = new RecordSet();
    	StringBuilder sb = new StringBuilder();
    	sb.append("SELECT DISTINCT requestid,nodeid,workflowid,workflowtype FROM workflow_currentoperator WHERE workflowtype<>1 AND isremark='0'");
    	sb.append(" AND requestid='").append(requestid).append("'");
    	if(!"".equals(appointNodeScope))
    		sb.append(" AND nodeid in (").append(appointNodeScope).append(")");
    	sb.append(" AND (EXISTS (SELECT 1 FROM workflow_nodelink t1 WHERE t1.wfrequestid IS NULL AND EXISTS (SELECT 1 FROM workflow_base t2 WHERE t1.workflowid=t2.id AND (t2.istemplate IS NULL OR t2.istemplate<>'1')) AND workflow_currentoperator.nodeid=t1.nodeid)");
    	sb.append(" OR EXISTS (SELECT 1 FROM workflow_nodelink t1 WHERE EXISTS (SELECT 1 FROM workflow_base t2 WHERE t1.workflowid=t2.id AND (t2.istemplate IS NULL OR t2.istemplate<>'1')) AND workflow_currentoperator.nodeid=t1.nodeid and workflow_currentoperator.requestid=t1.wfrequestid))");
    	sb.append(" AND (isreminded IS NULL OR isprocessed IS NULL OR isreminded_csh IS NULL) GROUP BY requestid,nodeid,workflowid,workflowtype ORDER BY requestid desc ,nodeid");
    	rs.executeSql(sb.toString());
        while(rs.next()) {
            int nodeid = Util.getIntValue(rs.getString("nodeid"));
			int workflowid = Util.getIntValue(rs.getString("workflowid"));
			int workflowtype = Util.getIntValue(rs.getString("workflowtype"));
			ArrayList userlist = new ArrayList();
			ArrayList usertypelist = new ArrayList();
			ArrayList agenttypelist = new ArrayList();
			ArrayList agentorbyagentidlist = new ArrayList();
			ArrayList isremindedlist = new ArrayList();
			ArrayList isreminded_cshlist = new ArrayList();
			ArrayList isprocessedlist = new ArrayList();
			ArrayList currentdatetimelist = new ArrayList();
			ArrayList idlist = new ArrayList();
			boolean isCanSubmit = true;
			sb.setLength(0);
			sb.append("SELECT * FROM workflow_currentoperator WHERE workflowtype<>1 AND isremark='0' AND (isreminded IS NULL OR isprocessed IS NULL OR isreminded_csh IS NULL)");
			sb.append(" AND requestid='").append(requestid).append("' AND nodeid='").append(nodeid).append("' ORDER BY requestid DESC,id");
			rs5.executeSql(sb.toString());
			while (rs5.next()) {
				String currentdatetimes = rs5.getString("receivedate") + " "
						+ rs5.getString("receivetime");
				String userids = rs5.getString("userid");
				String usertypes = rs5.getString("usertype");
				String agenttypes = rs5.getString("agenttype");
				String agentorbyagentids = rs5.getString("agentorbyagentid");
				String isremindeds = rs5.getString("isreminded");
				String isreminded_cshs = rs5.getString("isreminded_csh");// 超时后提醒





				String isprocesseds = rs5.getString("isprocessed");
				String ids = rs5.getString("id");

				WFForwardManager wfforwardMgr = new WFForwardManager();
				wfforwardMgr.setWorkflowid(workflowid);
				wfforwardMgr.setNodeid(nodeid);
				wfforwardMgr.setIsremark("0");
				wfforwardMgr.setRequestid(requestid);
				wfforwardMgr.setBeForwardid(Util.getIntValue(ids));
				wfforwardMgr.getWFNodeInfo();

				if (!wfforwardMgr.getCanSubmit()) {
					isCanSubmit = false;
					break;
				}

				userlist.add(userids);
				usertypelist.add(usertypes);
				agenttypelist.add(agenttypes);
				agentorbyagentidlist.add(agentorbyagentids);
				isremindedlist.add(isremindeds);
				isreminded_cshlist.add(isreminded_cshs);
				isprocessedlist.add(isprocesseds);
				currentdatetimelist.add(currentdatetimes);
				idlist.add(ids);
            }

            if (!isCanSubmit) continue;

            //会签特殊处理,为会签时,只取第一个用户来进行节点流转检查,并获得超时设置信息(因为其它用户获得的信息一样,不重复获取了)
            if (userlist.size() > 0) {
	            int userid = Util.getIntValue((String) userlist.get(0));
				int usertype = Util.getIntValue((String) usertypelist.get(0));
				int isreminded = Util.getIntValue((String) isremindedlist.get(0), 0);
				int isreminded_csh = Util.getIntValue((String) isreminded_cshlist.get(0), 0);
				int isprocessed = Util.getIntValue((String) isprocessedlist.get(0), 0);
				String currentdatetime = (String) currentdatetimelist.get(0);
				int nextlinkid = getNextNode(requestid, nodeid, userid, usertype);
				int language = 7;
				user.setUid(userid);
				user.setLogintype((usertype + 1) + "");
				user.setLastname(resource.getLastname(userid + ""));
				sb.setLength(0);
				sb.append("SELECT * FROM HrmResource WHERE id='").append(userid).append("'");
				rs1.executeSql(sb.toString());
				if (rs1.next()) {
					language = Util.getIntValue(rs1.getString("systemlanguage"), 7);
					user.setLanguage(language);
				}
				sb.setLength(0);
				sb.append("SELECT * FROM workflow_nodelink WHERE id='").append(nextlinkid).append("'");
	            rs1.executeSql(sb.toString());
	            if (rs1.next()) {
	                // 自动流转
					if (!hasNeedInputField(requestid, workflowid, nodeid)) {
						AutoFlowNextNode(requestid, nodeid, userlist, usertypelist,agenttypelist,agentorbyagentidlist,workflowtype, SystemEnv.getHtmlLabelName(18849, language));
					}
	            }
	        }
        }
    }

    /**
	 * 自动流转至下一操作者





	 * 
	 * @param requestid
	 * @param nodeid
	 * @param userlist
	 * @param usertypelist
	 * @param workflowtype
	 */
    private void AutoFlowNextNode(int requestid,int nodeid,ArrayList userlist,ArrayList usertypelist,ArrayList agenttypelist,ArrayList agentorbyagentidlist,int workflowtype,String ProcessorOpinion){
        RecordSet rs = new RecordSet();
        
        Calendar today = Calendar.getInstance();
        String currentdate = Util.add0(today.get(Calendar.YEAR), 4) + "-" +
                Util.add0(today.get(Calendar.MONTH) + 1, 2) + "-" +
                Util.add0(today.get(Calendar.DAY_OF_MONTH), 2);

        String currenttime = Util.add0(today.get(Calendar.HOUR_OF_DAY), 2) + ":" +
                Util.add0(today.get(Calendar.MINUTE), 2) + ":" +
                Util.add0(today.get(Calendar.SECOND), 2);
        //获得数据库服务器当前时间
        String sql="";
        if(rs5.getDBType().equals("oracle")){
            sql="select to_char(sysdate,'yyyy-mm-dd') currentdate,to_char(sysdate,'hh24:mi:ss') currenttime from dual";
        }else{
            sql="select convert(char(10),getdate(),20) currentdate,convert(char(8),getdate(),108) currenttime";
        }
        rs5.executeSql(sql);
        if(rs5.next()){
           currentdate=rs5.getString("currentdate");
           currenttime=rs5.getString("currenttime");
        }
        // 查询当前请求的一些基本信息





        rs3.executeProc("workflow_Requestbase_SByID", requestid + "");
        int passedgroups=0;
        int totalgroups=0;
        int creater=0;
        int creatertype=0;
        int workflowid=0;
        int nextnodeid=0;
        String nextnodetype="";
        int linkid=0;
        String status="";
        Hashtable operatorsht = new Hashtable();
        int rqMessageType=0;
        String mailrequestname = "";
        String mailMessageType = "0";
        int level=0;
        int isbill=0;
        int formid=0;
        int billid=0;
        String billtablename="";
        String nodetype="";
        String isAutoApprove ="0";//允许自动批准
        String isAutoCommit = "0";//允许处理节点自动提交
        int istest = 0;
        boolean hasEflowToAssignNode = false;
        int nodeattribute = 0; //当前节点节点属性
        if(rs3.next()){
            creater = Util.getIntValue(rs3.getString("creater"), 0);
            creatertype = Util.getIntValue(rs3.getString("creatertype"), 0);
            workflowid= Util.getIntValue(rs3.getString("workflowid"), 0);
            rqMessageType= Util.getIntValue(rs3.getString("MessageType"), 0);
            mailrequestname = Util.null2String(rs3.getString("requestname"));
            level=Util.getIntValue(rs3.getString("requestlevel"),0);
            mailMessageType = rs3.getString("mailMessageType");
            totalgroups = Util.getIntValue(rs3.getString("totalgroups"), 0);
            passedgroups = Util.getIntValue(rs3.getString("passedgroups"), 0);
            status = rs3.getString("status");
        }
        rs3.executeSql("select isbill,formid,isAutoApprove,isAutoCommit from workflow_base where id="+workflowid);
        if(rs3.next()){
            isbill=Util.getIntValue(rs3.getString("isbill"),0);
            formid=Util.getIntValue(rs3.getString("formid"),0);
            mailMessageType = rs3.getString("mailMessageType");
            isAutoApprove = Util.null2s(rs3.getString("isAutoApprove"), "0");
            isAutoCommit = Util.null2s(rs3.getString("isAutoCommit"), "0");
        }
        rs3.executeSql("select t1.nodetype,t2.nodeattribute from workflow_flownode t1 left join workflow_nodebase t2 on t1.nodeid = t2.id  where t1.workflowid="+workflowid+" and t1.nodeid="+nodeid);
        if(rs3.next()){
            nodetype=rs3.getString("nodetype");
            nodeattribute = Util.getIntValue(rs3.getString(2));
        }
        rs3.executeSql("select tablename from workflow_bill where id="+formid);
        if(rs3.next()){
            billtablename=Util.null2String(rs3.getString("tablename"));
        }
        if(!billtablename.trim().equals("")){
            rs3.executeSql("select id from "+billtablename+" where requestid="+requestid);
            if(rs3.next()){
                billid=Util.getIntValue(rs3.getString("id"));
            }
        }
        
        int mgrID = this.updateManagerField(requestid, formid, isbill, Util.getIntValue((String)userlist.get(userlist.size()-1)));
        
        boolean isWorkFlowToDoc = false;
        
        rs.executeSql("select * from workflow_addinoperate  where workflowid="+workflowid+" and isnode=1 and objid="+nodeid+" and ispreadd='0' and type=2 and customervalue='action.WorkflowToDoc' ");                                  
        if(rs.next()) {
            isWorkFlowToDoc=true;
        }
        
        //节点后自动赋值处理 START
        try {
            RequestCheckAddinRules requestCheckAddinRules = new RequestCheckAddinRules();
            requestCheckAddinRules.resetParameter();
            requestCheckAddinRules.setRequestid(requestid);
            requestCheckAddinRules.setObjid(nodeid);
            requestCheckAddinRules.setObjtype(1);
            requestCheckAddinRules.setIsbill(isbill);
            requestCheckAddinRules.setFormid(formid);
            requestCheckAddinRules.setIspreadd("0");
            requestCheckAddinRules.checkAddinRules();
        } catch (Exception erca) {
            log.error("节点后赋值处理错误:"+erca.getMessage());
        }
        
        //节点后自动赋值处理 END
        
        // 如果是提交节点, 查询通过了的组数
        String groupdetailids="";
        rs3.executeSql("select count(distinct groupid) from workflow_currentoperator where isremark = '0' and requestid=" + requestid + " and userid=" + userlist.get(0) + " and usertype=" + usertypelist.get(0));
		if (rs3.next()) passedgroups += Util.getIntValue(rs3.getString(1), 0);
		
		int oboindex = 0;
        for (int i=0; i<userlist.size(); i++) {
            int tempuserid = Util.getIntValue((String)userlist.get(i));
            int tempusertype = Util.getIntValue((String)usertypelist.get(i));
            
    		//判断该人所在组是否含有依次逐个递交的组，如果有一个，则passedgroups-1，并且进入得到下个操作者的方法
    		rs3.execute("select distinct groupdetailid,groupid from workflow_currentoperator where isremark = '0' and requestid=" + requestid + " and userid=" + tempuserid + " and usertype=" + tempusertype);
    
    		while (rs3.next())
    		{
    			rs4.execute("select * from workflow_groupdetail where id="+rs3.getInt("groupdetailid"));
    			if (rs4.next())
    			{
                    int type = rs4.getInt("type");
    				int objid = rs4.getInt("objid");
    				int leveln = rs4.getInt("signorder");
    				if (WFPathUtil.isContinuousProcessing(type) && leveln == 2)
    				{    //判断是否还有剩余节点
    					rs4.execute("select * from workflow_agentpersons where requestid="+requestid+" and (groupdetailid="+rs3.getInt("groupdetailid")+" or groupdetailid is null)");
    					if (rs4.next()&&!rs4.getString("receivedPersons").equals(""))
    					{
                            passedgroups--;
    						groupdetailids=groupdetailids.equals("")?rs3.getString("groupdetailid")+"_"+rs3.getString("groupid"):groupdetailids+","+rs3.getString("groupdetailid")+"_"+rs3.getString("groupid");
    						oboindex = i;
    					}
    				}
    
    			}
    		}
        }
        // 查询下一个节点的操作者





        boolean hasnextnodeoperator =false;
        nextnodeids = new ArrayList();
        nextnodetypes = new ArrayList();
        nextlinkids = new ArrayList();
        nextlinknames = new ArrayList();
        operatorshts = new ArrayList();
        nextnodeattrs = new ArrayList();
        nextnodepassnums = new ArrayList();
        linkismustpasss = new ArrayList();
        boolean canflowtonextnode=true;
        int nextnodeattr=0;
        WFLinkInfo wflinkinfo=new WFLinkInfo();
        RequestNodeFlow requestNodeFlow = new RequestNodeFlow();
        RequestManager rm = new RequestManager();
        //System.out.println(passedgroups+"|"+totalgroups);
        if(groupdetailids.equals("")){
            //System.out.println("aaaaaaaaaaa");
            requestNodeFlow.setRequestid(requestid);
            requestNodeFlow.setNodeid(nodeid);
            requestNodeFlow.setNodetype(nodetype);
            requestNodeFlow.setWorkflowid(workflowid);
            requestNodeFlow.setUserid(Util.getIntValue((String)userlist.get(oboindex)));
            requestNodeFlow.setUsertype(Util.getIntValue((String)usertypelist.get(oboindex)));
            requestNodeFlow.setCreaterid(creater);
            requestNodeFlow.setCreatertype(creatertype);
            requestNodeFlow.setIsbill(isbill);
            requestNodeFlow.setBillid(billid);
            requestNodeFlow.setFormid(formid);
            requestNodeFlow.setBilltablename(billtablename);
            requestNodeFlow.setRecordSet(rs3);
			requestNodeFlow.setIsreject(0);
			requestNodeFlow.setIsintervenor("1");
            requestNodeFlow.getNextNodes();
            nextnodeids=requestNodeFlow.getNextnodeids();
            nextnodetypes=requestNodeFlow.getNextnodetypes();
            nextlinkids=requestNodeFlow.getNextlinkids();
            nextlinknames=requestNodeFlow.getNextlinknames();
            operatorshts=requestNodeFlow.getOperatorshts();
            nextnodeattrs=requestNodeFlow.getNextnodeattrs();
            nextnodepassnums=requestNodeFlow.getNextnodepassnums();
            linkismustpasss=requestNodeFlow.getLinkismustpasss();
            hasEflowToAssignNode = requestNodeFlow.isHasEflowToAssignNode();
            if(nextnodeids.size()>0) hasnextnodeoperator=true;
            //System.out.println(hasnextnodeoperator);
        }else{
                //System.out.println("bbbbbbbbbbbbb");
                requestNodeFlow.setRequestid(requestid);
                requestNodeFlow.setNodeid(nodeid);
                requestNodeFlow.setNodetype(nodetype);
                requestNodeFlow.setWorkflowid(workflowid);
                requestNodeFlow.setUserid(Util.getIntValue((String)userlist.get(0)));
                requestNodeFlow.setUsertype(Util.getIntValue((String)usertypelist.get(0)));
                requestNodeFlow.setCreaterid(creater);
                requestNodeFlow.setCreatertype(creatertype);
                requestNodeFlow.setIsbill(isbill);
                requestNodeFlow.setBillid(billid);
                requestNodeFlow.setFormid(formid);
                requestNodeFlow.setBilltablename(billtablename);
                requestNodeFlow.setRecordSet(rs3);
				requestNodeFlow.setIsintervenor("1");
                hasnextnodeoperator = requestNodeFlow.getNextOrderOperator(groupdetailids);
                if(hasnextnodeoperator){
                    nextnodeids.add(""+nodeid);
                    nextnodetypes.add(nextnodetype);
                    nextlinkids.add(""+requestNodeFlow.getNextLinkid());
                    operatorshts.add(requestNodeFlow.getOperators());
                    nextlinknames.add(status);
                    nextnodeattr=wflinkinfo.getNodeAttribute(nodeid);
                    nextnodeattrs.add(""+nextnodeattr);
                    nextnodepassnums.add("2");
                    linkismustpasss.add("1");
                }
        }
        if (hasnextnodeoperator) {
             //获得所有下一个节点的nodeid，用于查询流程生成计划任务设置





             String wf_nextnodeids = "";
             for(int i=0;i<nextnodeids.size();i++){
                nextnodeid = Util.getIntValue((String)nextnodeids.get(i));
                //System.out.println("nextnodeid:"+nextnodeid);
                wf_nextnodeids += (""+nextnodeid+", ");     //TD9427
                
                nextnodetype = (String) nextnodetypes.get(i);
                status = (String) nextlinknames.get(i);
                nextnodeattr = Util.getIntValue((String) nextnodeattrs.get(i), 0);
                linkid = Util.getIntValue((String) nextlinkids.get(i));
                operatorsht = (Hashtable) operatorshts.get(i);
                totalgroups = operatorsht.size();
                if (groupdetailids.equals("") && (nextnodeattr == 3 || nextnodeattr == 4)){
                    wflinkinfo.setSrc("submit");
                    canflowtonextnode = wflinkinfo.FlowToNextNode(requestid, nodeid, nextnodeid, "" + nextnodeattr, Util.getIntValue((String) nextnodepassnums.get(i)), Util.getIntValue((String) linkismustpasss.get(i)));
                }
                //System.out.println(nodeid+"|"+nextnodeid+"|"+linkid+"|"+totalgroups);
                rs.executeSql("select * from workflow_addinoperate  where workflowid="+workflowid+" and ((isnode=0 and objid="+linkid+" and ispreadd='0') or (isnode=1 and objid="+nextnodeid+" and ispreadd='1' )) and type=2 and customervalue='action.WorkflowToDoc' ");     
                if(rs.next()){
                    isWorkFlowToDoc=true;
                }
                /*
                // 出口自动赋值处理





                try {
                    RequestCheckAddinRules requestCheckAddinRules = new RequestCheckAddinRules();
                    requestCheckAddinRules.resetParameter();
                    requestCheckAddinRules.setRequestid(requestid);
                    requestCheckAddinRules.setObjid(linkid);
                    requestCheckAddinRules.setObjtype(0);               // 1: 节点自动赋值 0 :出口自动赋值





                    requestCheckAddinRules.setIsbill(isbill);
                    requestCheckAddinRules.setFormid(formid);
                    requestCheckAddinRules.setIspreadd("0");//xwj for td3130 20051123
                    requestCheckAddinRules.checkAddinRules();
                } catch (Exception erca) {
                        log.error("出口赋值处理错误:"+erca.getMessage());
                }
                */
                
                rm.setUser(user);
                rm.setWorkflowid(workflowid);
                rm.setRequestid(requestid);
                rm.setSrc("submit");
                rm.setIscreate("0");
                rm.setRequestid(requestid);
                rm.setWorkflowid(workflowid);
                rm.setIsremark(0);
                rm.setFormid(formid);
                rm.setIsbill(isbill);
                rm.setBillid(billid);
                rm.setNodeid(nodeid);
                rm.setNodetype(nodetype);
                rm.setNodeattribute(nodeattribute);
                rm.setIsAutoApprove(isAutoApprove);
                rm.setIsAutoCommit(isAutoCommit);
                rm.setWorkflowtype(String.valueOf(workflowtype));
                rs.executeSql("select * from workflow_requestbase where requestid=" + requestid);
                if (rs.next()) {
                    rm.setRequestname(rs.getString("requestname"));
                    rm.setRequestlevel(rs.getString("requestlevel"));
                    rm.setMessageType(rs.getString("messageType"));
                    rm.setCreater(Util.getIntValue(rs.getString("creater")));
                    rm.setCreatertype(Util.getIntValue(rs.getString("creatertype")));
                    //是否允许退回时选择节点
                }
                
                //出口赋值





                try {
                    //linkid=Util.getIntValue((String)nextlinkids.get(n));
                    RequestCheckAddinRules requestCheckAddinRules = new RequestCheckAddinRules();
                    //add by cyril on 2008-07-28 for td:8835 事务无法开启查询,只能传入
                    //requestCheckAddinRules.setTrack(isTrack);
//                    requestCheckAddinRules.setStart(isStart);
                    requestCheckAddinRules.setNodeid(nodeid);
                    //end by cyril on 2008-07-28 for td:8835
                    requestCheckAddinRules.resetParameter();
                    requestCheckAddinRules.setRequestid(requestid);
                    requestCheckAddinRules.setWorkflowid(workflowid);
                    requestCheckAddinRules.setObjid(linkid);
                    requestCheckAddinRules.setObjtype(0);               // 1: 节点自动赋值 0 :出口自动赋值





                    requestCheckAddinRules.setIsbill(isbill);
                    requestCheckAddinRules.setFormid(formid);
                    requestCheckAddinRules.setIspreadd("0");//xwj for td3130 20051123
                    requestCheckAddinRules.setUser(user);//add by fanggsh 20061016 fot TD5121
                    String clientIp = "127.0.0.1";
                    requestCheckAddinRules.setClientIp(clientIp);//add by fanggsh 20061016 fot TD5121
                    requestCheckAddinRules.setSrc("submit");//add by fanggsh 20061016 fot TD5121
                    requestCheckAddinRules.setRequestManager(rm);
                    requestCheckAddinRules.checkAddinRules();
                    
//                    requestCheckAddinRulesMap=new HashMap();
//                    requestCheckAddinRulesMap.put("objId",""+linkid);
//                    requestCheckAddinRulesMap.put("objType","0");// 1: 节点自动赋值 0 :出口自动赋值





//                    requestCheckAddinRulesMap.put("isPreAdd","0");          
//                    requestCheckAddinRulesList.add(requestCheckAddinRulesMap);
                } catch (Exception erca) {
                    writeLog(erca);
                    log.error("出口赋值处理错误:"+erca.getMessage());
                }
                
                //节点自动赋值处理





                try {
                    //由于objtype为"1: 节点自动赋值",不为"0 :出口自动赋值"，不用改变除状态外的文档相关信息，故可不用给user、clienIp、src赋值   fanggsh TD5121
                    RequestCheckAddinRules requestCheckAddinRules = new RequestCheckAddinRules();
                    requestCheckAddinRules.resetParameter();
                   //add by cyril on 2008-07-28 for td:8835 事务无法开启查询,只能传入
//                   requestCheckAddinRules.setTrack(isTrack);
//                   requestCheckAddinRules.setStart(isStart);
                   requestCheckAddinRules.setNodeid(nodeid);
                   //end by cyril on 2008-07-28 for td:8835
                    requestCheckAddinRules.setRequestid(requestid);
                    requestCheckAddinRules.setWorkflowid(workflowid);
                    requestCheckAddinRules.setObjid(nextnodeid);
                    requestCheckAddinRules.setObjtype(1);
                    requestCheckAddinRules.setIsbill(isbill);
                    requestCheckAddinRules.setFormid(formid);
                    requestCheckAddinRules.setIspreadd("1");
                    //因为rm中没有nextnodeid，导致节点前附加操作不执行，此处填上
                    rm.setNextNodeid(nextnodeid);
                    requestCheckAddinRules.setRequestManager(rm);
                    requestCheckAddinRules.setUser(user);                                   
                    requestCheckAddinRules.checkAddinRules();
                    
//                    requestCheckAddinRulesMap=new HashMap();
//                    requestCheckAddinRulesMap.put("objId",""+nextnodeid);
//                    requestCheckAddinRulesMap.put("objType","1");// 1: 节点自动赋值 0 :出口自动赋值





//                    requestCheckAddinRulesMap.put("isPreAdd","1");         
//                    requestCheckAddinRulesList.add(requestCheckAddinRulesMap);
                } catch (Exception erca) {
                    writeLog(erca);
                    log.error("节点赋值处理错误:"+erca.getMessage());                
                }
             }
             

             try {
                 //查询流程生成计划任务（总体）设置，取得到达nextnodeids时和离开nodeid时生成计划任务的情况，生成计划任务





                 int isusedworktask = Util.getIntValue(getPropValue("worktask","isusedworktask"), 0);
                 if(isusedworktask == 1){
                        sql = "select * from workflow_createtask where wfid=" + workflowid + " and ((nodeid=" + nodeid + " and changetime=2) or (changetime=1 and nodeid in ("+wf_nextnodeids+"0)))";
                        rs.execute(sql);
                        while(rs.next()){
                            int creatertype_tmp = Util.getIntValue(rs.getString("creatertype"), 0);
                            if(creatertype_tmp == 0){
                                continue;
                            }
                            int createtaskid_tmp = Util.getIntValue(rs.getString("id"), 0);
                            int wffieldid_tmp = Util.getIntValue(rs.getString("wffieldid"), 0);
                            int taskid_tmp = Util.getIntValue(rs.getString("taskid"), 0);
                            int changemode_tmp = Util.getIntValue(rs.getString("changemode"), 0);
                            int changenodeid_tmp = Util.getIntValue(rs.getString("nodeid"), 0);
                            int changetime_tmp = Util.getIntValue(rs.getString("changetime"), 0);
                            if(changenodeid_tmp!=nodeid && (","+wf_nextnodeids+",").indexOf(","+changenodeid_tmp+",")>-1){//首先保证触发节点是操作后到达的节点，并且触发节点在到达的节点之中
                                if(changetime_tmp==1 && changemode_tmp==2){
                                    continue;
                                }
                            }
                            if(changenodeid_tmp==nodeid){//如果是离开节点
                                if(changetime_tmp==2 && changemode_tmp==2){
                                    continue;
                                }
                            }
                            RequestCreateByWF requestCreateByWF = new RequestCreateByWF();
                            requestCreateByWF.setWf_formid(formid);
                            requestCreateByWF.setWf_isbill(isbill);
                            requestCreateByWF.setWf_wfid(workflowid);
                            requestCreateByWF.setWf_requestid(requestid);
                            requestCreateByWF.setWt_creatertype(creatertype_tmp);
                            requestCreateByWF.setWt_creater(creater);
                            requestCreateByWF.setWf_fieldid(wffieldid_tmp);
                            requestCreateByWF.setWt_wtid(taskid_tmp);
                            requestCreateByWF.setCreatetaskid(createtaskid_tmp);
                            requestCreateByWF.createWT();
                     }
                 }
             } catch (Exception e) {
                e.printStackTrace();
            }
             
            //更新currentoperator
            for(int i=0;i<userlist.size();i++){
            rs3.executeSql("select distinct groupid from workflow_currentoperator where isremark = '0' and requestid=" + requestid + " and userid=" + userlist.get(i) + " and usertype=" + usertypelist.get(i)+" and nodeid="+nodeid);
            while (rs3.next()) {
                int tmpgroupid = Util.getIntValue(rs3.getString(1), 0);
                rs4.executeProc("workflow_CurOpe_UpdatebySubmit", "" +userlist.get(i) +Util.getSeparator() + requestid + Util.getSeparator() + tmpgroupid+Util.getSeparator()+nodeid+Util.getSeparator()+"0" + Util.getSeparator() + currentdate + Util.getSeparator() + currenttime);
            }
            }
            innodeids="";
            if (canflowtonextnode && (nextnodeattr == 3 || nextnodeattr == 4)) {
                innodeids = wflinkinfo.getSummaryNodes(nextnodeid, workflowid, "",requestid);
                if (innodeids.equals("")) innodeids = "0";
                rs4.executeSql("update workflow_currentoperator set isremark='2' where isremark='0' and requestid=" + requestid + " and nodeid in(" + innodeids + ")");
                rs4.executeSql("update workflow_currentoperator set isremark='2' where isremark = '5' and requestid=" + requestid + " and nodeid in(" + innodeids + ")");
            }
            //更新requestbase
            sql = " update workflow_requestbase set " +
                            " lastnodeid = " + nodeid +
                            " ,lastnodetype = '" + nodetype ;
           if (canflowtonextnode) {
                if (nextnodeattr == 1) {
                    sql += "' ,currentnodeid = " + nextnodeid +
                            " ,currentnodetype = '" + nextnodetype;
                    status = SystemEnv.getHtmlLabelName(21394, user.getLanguage());
                } else if (nextnodeattr == 2) {
                    sql += "' ,currentnodeid = " + nextnodeid +
                            " ,currentnodetype = '" + nextnodetype;
                    status = SystemEnv.getHtmlLabelName(21395, user.getLanguage());
                } else {
                    sql += "' ,currentnodeid = " + nextnodeid +
                            " ,currentnodetype = '" + nextnodetype;
                }
            } else {
                status = SystemEnv.getHtmlLabelName(21395, user.getLanguage());
            }
            sql += "' ,status = '" + status + "' " +
                            " ,passedgroups = 0" +
                            " ,totalgroups = " + totalgroups +
                            " ,lastoperator = " + userlist.get(userlist.size()-1) +
                            " ,lastoperatedate = '" + currentdate + "' " +
                            " ,lastoperatetime = '" + currenttime + "' " +
                            " ,lastoperatortype = " + usertypelist.get(usertypelist.size()-1) +
                            " where requestid = " + requestid;
            rs3.executeSql(sql);
            //操作人插入操作



            //流程自动流转验证
            Map<Integer,AutoApproveParams> nodeInfoCache = new HashMap<Integer,AutoApproveParams>();
            if(canflowtonextnode) setOperator(requestid,workflowid,workflowtype,nodeid,nodeInfoCache,rm);
			String temp_logtype = "2";
			if(nodetype.equals("1")){
			   temp_logtype = "0";
			}
            for(int n=0;n<nextnodeids.size();n++){
                nextnodeid=Util.getIntValue((String)nextnodeids.get(n));
                nextnodeattr = Util.getIntValue((String) nextnodeattrs.get(n), 0);
                for(int i=0;i<userlist.size();i++){
                    //处理新到达流程提醒





                    poppupRemindInfoUtil.updatePoppupRemindInfo(Util.getIntValue((String)userlist.get(i)),0,(String)usertypelist.get(i),requestid);
                    writeWFLog(requestid,workflowid,nodeid,Util.getIntValue((String)userlist.get(i)),Util.getIntValue((String)usertypelist.get(i)),Util.getIntValue((String)agenttypelist.get(i)),Util.getIntValue((String)agentorbyagentidlist.get(i)),nextnodeid,currentdate,currenttime,ProcessorOpinion,temp_logtype,canflowtonextnode,nextnodeattr);
                }
            }
			//add by liaodong for qc in 2013年11月6日 start //插入抄送日志





               if(operator89List.size()>0){
            	  writeWFLog(requestid,workflowid,nodeid,0,0,nextnodeid,currentdate,currenttime,ProcessorOpinion,"t",canflowtonextnode,nextnodeattr);
               }
        	//end
            //处理超时流程提醒
            for(int i=0;i<wfremindusers.size();i++){
                poppupRemindInfoUtil.updatePoppupRemindInfo(Util.getIntValue((String)wfremindusers.get(i)),10,(String)wfusertypes.get(i),requestid);
            }
            if (canflowtonextnode&&nextnodetype.equals("3")) {
                    String Procpara = "" + creater + Util.getSeparator() + creatertype + Util.getSeparator() + requestid;

                    if(!operatorsWfEnd.contains(creater+"_"+creatertype)){//xwj for td3450 20060111
                    	poppupRemindInfoUtil.addPoppupRemindInfo(creater,1,""+creatertype,requestid,requestcominfo.getRequestname(requestid+""));
                    }
                    //modify by xhheng @20050520 for TD1725,添加条件 isremark='0' 使能区分历史操作人和归档人





                    // 2005-03-24 Guosheng for TD1725**************************************
                    rs3.executeSql("update  workflow_currentoperator  set isremark='4'  where isremark='0' and requestid = " +  requestid);
                    rs3.executeSql("update  workflow_currentoperator  set iscomplete=1  where requestid = " +  requestid );
            }
            //将已查看操作人的查看状态置为（-1：新提交未查看）
               //TD4294  删除workflow_currentoperator表中orderdate、ordertime列 fanggsh begin
            //rs.executeSql("update workflow_currentoperator set viewtype =-1,orderdate='" + currentdate + "' ,ordertime='" + currenttime + "'  where requestid=" + requestid + " and userid<>" + userid + " and viewtype=-2");
            //rs.executeSql("update workflow_currentoperator set viewtype =-1   where requestid=" + requestid + " and userid<>" + userid + " and viewtype=-2");
               //TD4294  删除workflow_currentoperator表中orderdate、ordertime列 fanggsh end

            //将自己的查看状态置为（-2：已提交已查看）
            //by ben 2006-03-27加上nodeid的条件限制后一个节点有相同于当前操作人时只设置当前的节点





            //rs.executeSql("update workflow_currentoperator set viewtype =-2 where requestid=" + requestid + "  and userid=" + userid + " and usertype = "+usertype+" and viewtype<>-2");
            System.out.println("进入："+requestid+" nextnodeids.size:"+nextnodeids.size());
                //add by xhheng @20050125 for 消息提醒 request06 ,短信发送





            for(int i=0;i<nextnodeids.size();i++){
                nextnodeid=Util.getIntValue((String)nextnodeids.get(i));
                
				RecordSetTrans rst = new RecordSetTrans();
                try {
                	rst.setAutoCommit(false);
                    String src = "submit";
                    SendMsgAndMail sendMsgAndMail = new SendMsgAndMail();
                    
                    rs3.executeSql("select operator from workflow_requestLog where logtype in ('0','1','2','9') and nodeid="+nodeid+" and requestid="+requestid+" order by LOGID desc");
                    rs3.next();
                    int tmpuserid = Util.getIntValue(rs3.getString(1),1);
                    //User tmpuser = getUser(tmpuserid);
                    //发送短信





                    sendMsgAndMail.sendMsg(rst,requestid,nextnodeid,user,src,nextnodetype);
                    // 邮件提醒
        		    sendMsgAndMail.sendMail(rst,workflowid,requestid,nextnodeid,null,null,false,src,nextnodetype,user);
        		    rst.commit();
        		    
        		    System.out.println("发送短信====================="+requestid);
    			} catch (Exception e) {
					rst.rollback();
    				System.out.println("发送短信====================="+e.getMessage());
    				writeLog("超时短信提醒："+e);
    			}
    			
                 //子流程归档设置，数据汇总主流程，插入明细相关信息，不考虑主流程明细是否只读qc:
		         try{
		  		   //是否开启子流程全部归档才能提交,子流程归档时调用
		  		   if(WFSubDataAggregation.checkSubProcessSummary(requestid)){
		  			   String cmainRequestId = SubWorkflowTriggerService.getMainRequestId(requestid);
		  			   if (cmainRequestId != null && !cmainRequestId.isEmpty()) {
		  				   if (nextnodetype.equals("3")) {
		  					   WFSubDataAggregation.addMainRequestDetail(cmainRequestId,requestid+"",-1,user);
		  				   }
		  			   }
		  		   }
		  		   //主流程到达汇总节点时判断子流程是否已归档，已归档则进行汇总
		  		   List<String> subList = WFSubDataAggregation.getSubRequestIdByMain(requestid,workflowid,nextnodeid);
		  		   if(subList.size()>0){
		  			   for(int r=0;r<subList.size();r++){
		  				   WFSubDataAggregation.addMainRequestDetail(requestid+"",subList.get(r),nextnodeid,user);
		  			   }
		  		   }
		  		   
		         }catch(Exception e){
		      	   e.printStackTrace();
		         }
                
	            // 处理共享信息
				try {
	                RequestAddShareInfo shareinfo = new RequestAddShareInfo();
					shareinfo.setRequestid(requestid);
	                shareinfo.SetWorkFlowID(workflowid);
	                shareinfo.SetNowNodeID(nodeid);
	                if(nextnodeid==0)
	                	shareinfo.SetNextNodeID(nodeid);
	                else
	                	shareinfo.SetNextNodeID(nextnodeid);
	                shareinfo.setIsbill(isbill);
					shareinfo.setUser(user);
	                shareinfo.SetIsWorkFlow(1);
	                shareinfo.setBillTableName(billtablename);
	                shareinfo.setHaspassnode(true);
	
					shareinfo.addShareInfo();
				}catch(Exception easi) {
				}
            }
            
            
            String hasTriggeredSubwf = "";//已触发子流程，防止死循环
            //触发子流程





            ArrayList nodeidList_sub = new ArrayList();
            ArrayList triggerTimeList_sub = new ArrayList();
            ArrayList hasTriggeredSubwfList_sub = new ArrayList();     
            
            //触发日程
            ArrayList nodeidList_wp = new ArrayList();
            ArrayList createTimeList_wp = new ArrayList();
            
            //triggerStatus  ""  成功
            //               "1" 子流程创建人无值





            String triggerStatus="";
            boolean nextNodeHasCurrentNode=false;
            for(int n=0;n<nextnodeids.size();n++){
                nextnodeid=Util.getIntValue((String)nextnodeids.get(n));
                if(nextnodeid==nodeid){
                    nextNodeHasCurrentNode=true;
                }
            }
            if(nextNodeHasCurrentNode==false && nextnodeid>0 && nextnodeid!=nodeid){
                nodeidList_sub.add(""+nodeid);
                triggerTimeList_sub.add("2");
                hasTriggeredSubwfList_sub.add(hasTriggeredSubwf);
                //triggerStatus=subwfTriggerManager.TriggerSubwf(this,nodeid,"2",hasTriggeredSubwf,user);
            }
            
            if(nextnodeids!=null && nextnodeids.size()>0 && !nextnodeids.contains(""+nodeid)){
                nodeidList_wp.add(""+nodeid);
                createTimeList_wp.add("2");//离开节点
            }
            
            for(int n=0;n<nextnodeids.size();n++){
                nextnodeid=Util.getIntValue((String)nextnodeids.get(n));
                if(nextnodeid>0&&nextnodeid!=nodeid){
                    nodeidList_sub.add(""+nextnodeid);
                    triggerTimeList_sub.add("1");
                    hasTriggeredSubwfList_sub.add(hasTriggeredSubwf);
                    //triggerStatus=subwfTriggerManager.TriggerSubwf(this,nextnodeid,"1",hasTriggeredSubwf,user);

                    //TD13304 在这里记录需要触发日程的节点和触发类型（到达节点或离开节点），到流程事务外处理
                    nodeidList_wp.add(""+nextnodeid);
                    createTimeList_wp.add("1");//到达节点
                }
            }
            
            /**
             * 触发子流程 为了防止死锁，移到事物结束后处理。子流程触发失败，不影响主流程流转





             * chujun
             * Start
             */
            try{
                if (nodeidList_sub.size() > 0) {
                    rm = new RequestManager();
                    rm.setUser(user);
                    rm.setWorkflowid(workflowid);
                    rm.setRequestid(requestid);
                    rm.setSrc("submit");
                    rm.setIscreate("0");
                    rm.setRequestid(requestid);
                    rm.setWorkflowid(workflowid);
                    rm.setIsremark(0);
                    rm.setFormid(formid);
                    rm.setIsbill(isbill);
                    rm.setBillid(billid);
                    rm.setNodeid(nodeid);
                    rm.setNodetype(nodetype);
                    rs.executeSql("select * from workflow_requestbase where requestid=" + requestid);
                    if (rs.next()) {
                        rm.setRequestname(rs.getString("requestname"));
                        rm.setRequestlevel(rs.getString("requestlevel"));
                        rm.setMessageType(rs.getString("messageType"));
                        rm.setCreater(Util.getIntValue(rs.getString("creater")));
                        rm.setCreatertype(Util.getIntValue(rs.getString("creatertype")));
                        //是否允许退回时选择节点
                    }
                    
                    for(int i=0; i<nodeidList_sub.size(); i++){
                        int nodeid_tmp = Util.getIntValue((String)nodeidList_sub.get(i), 0);
                        String triggerTime_tmp = Util.null2String((String)triggerTimeList_sub.get(i));
                        String hasTriggeredSubwf_tmp = Util.null2String((String)hasTriggeredSubwfList_sub.get(i));
                        SubWorkflowTriggerService triggerService = new SubWorkflowTriggerService(rm, nodeid_tmp, hasTriggeredSubwf, user);
                        triggerService.triggerSubWorkflow("1", triggerTime_tmp);
                    }
                }
            }catch(Exception e){
                e.printStackTrace();
            }
            
            /**
             * 流程触发日程 TD13304
             */
            try{
                String clientip = "127.0.0.1";
                String sqlExt = "";
                for(int i=0; i<nodeidList_wp.size(); i++){
                    String nodeid_tmp = Util.null2String((String)nodeidList_wp.get(i));
                    String createTime_tmp = Util.null2String((String)createTimeList_wp.get(i));
                    sqlExt += " (nodeid="+nodeid_tmp+" and changetime="+createTime_tmp+") or";
                }
                if(!"".equals(sqlExt)){
                    CreateWorkplanByWorkflow createWorkplanByWorkflow = null;
                    sqlExt = sqlExt.substring(0, sqlExt.length()-2);
                    RecordSet rs_wp = new RecordSet();
                    rs_wp.execute("select * from workflow_createplan where wfid="+ workflowid +" and ("+sqlExt+") order by id");
                    while(rs_wp.next()){
                        int createplanid = Util.getIntValue(rs_wp.getString("id"), 0);
                        int plantypeid_tmp = Util.getIntValue(rs_wp.getString("plantypeid"), 0);
                        int creatertype_tmp = Util.getIntValue(rs_wp.getString("creatertype"), 0);
                        int wffieldid_tmp = Util.getIntValue(rs_wp.getString("wffieldid"), 0);
                        createWorkplanByWorkflow = new CreateWorkplanByWorkflow();
                        createWorkplanByWorkflow.setCreateplanid(createplanid);
                        createWorkplanByWorkflow.setWorkplantypeid(plantypeid_tmp);
                        createWorkplanByWorkflow.setWp_creatertype(creatertype_tmp);
                        createWorkplanByWorkflow.setWf_fieldid(wffieldid_tmp);
                        createWorkplanByWorkflow.setWf_formid(formid);
                        createWorkplanByWorkflow.setWf_isbill(isbill);
                        createWorkplanByWorkflow.setWf_requestid(requestid);
                        createWorkplanByWorkflow.setWf_wfid(workflowid);
                        createWorkplanByWorkflow.setUser(user);
                        createWorkplanByWorkflow.setRemoteAddr(clientip);
                        createWorkplanByWorkflow.createWorkplan();
                    }
                }
            }catch(Exception e){
                e.printStackTrace();
            }
            
            //流程归档，删除Workflow_DocSource数据
			if (canflowtonextnode&&nextnodetype.equals("3")) {
				rs3.execute("select docRightByOperator from workflow_base where id="+workflowid);
				if(rs3.next()){
					if(Util.getIntValue(rs3.getString("docRightByOperator"),0)==1){
						rs3.execute("delete from Workflow_DocSource where requestid =" + requestid );
					}
				}
			}
			
			//如果节点后附加操作为流程存为文档，那么等所有操作执行之后，来执行存为文档操作 
            if(isWorkFlowToDoc && nodeid != nextnodeid) {
                 Action action= (Action)StaticObj.getServiceByFullname("action.WorkflowToDoc", Action.class);
                 RequestService requestService=new  RequestService();
                 //String msg=action.execute(requestService.getRequest(requestid));
                 String msg=action.execute(requestService.getRequest(requestid, 999));                        
            }
            writeLog("====2636====开始超时流转后自动提交，符合的记录数为："  + nodeInfoCache.size());
            if(nodeInfoCache.size() > 0){
			    WFAutoApproveThreadPoolUtil.getFixedThreadPool().execute(new WFAutoApproveUtils(rm,nodeInfoCache.get(nodeInfoCache.keySet().iterator().next())));
			}
        }else {
        	//还原Manager
            this.rollbackUpdatedManagerField(requestid, formid, isbill, mgrID);
		}
	}

    /**
     * 插入下一操作人





     * @param requestid
     * @param workflowid
     * @param workflowtype
     * @param nodeid
     */
    public void setOperator(int requestid,int workflowid,int workflowtype,int nodeid,Map<Integer,AutoApproveParams> nodeInfoCache,RequestManager rm)
     {
    	wfAgentCondition wfAgentCondition=new wfAgentCondition();
    	WFAutoApproveUtils wfautoApproveUtil = new WFAutoApproveUtils();
    	RecordSetTrans rst=new RecordSetTrans();
        rst.setAutoCommit(false);
    	ArrayList poppuplist=new ArrayList();
		 operator89List = new ArrayList();//add by liaodong for qc80034 in 2013-11-07  start
         Calendar today = Calendar.getInstance();
        String currentdate = Util.add0(today.get(Calendar.YEAR), 4) + "-" +
                Util.add0(today.get(Calendar.MONTH) + 1, 2) + "-" +
                Util.add0(today.get(Calendar.DAY_OF_MONTH), 2);

        String currenttime = Util.add0(today.get(Calendar.HOUR_OF_DAY), 2) + ":" +
                Util.add0(today.get(Calendar.MINUTE), 2) + ":" +
                Util.add0(today.get(Calendar.SECOND), 2);
         //获得数据库服务器当前时间
        String sql="";
        if(rs5.getDBType().equals("oracle")){
            sql="select to_char(sysdate,'yyyy-mm-dd') currentdate,to_char(sysdate,'hh24:mi:ss') currenttime from dual";
        }else{
            sql="select convert(char(10),getdate(),20) currentdate,convert(char(8),getdate(),108) currenttime";
        }
        rs5.executeSql(sql);
        if(rs5.next()){
           currentdate=rs5.getString("currentdate");
           currenttime=rs5.getString("currenttime");
        }
        for(int n=0;n<nextnodeids.size();n++){
            int nextnodeid=Util.getIntValue((String)nextnodeids.get(n),0);
            Hashtable operatorsht=(Hashtable)operatorshts.get(n);
            String nextnodetype=Util.null2String((String)nextnodetypes.get(n));
            int nextnodeattr=Util.getIntValue((String)nextnodeattrs.get(n),0);
            rm.setNextNodeid(nextnodeid);
            rm.setNextNodetype(nextnodetype);
            ArrayList operatorsWfNew = new ArrayList();
           char flag=Util.getSeparator();
         //操作人更新结束





            int showorder = 0;
            /*---------added by xwj for td2850 begin----*/
            TreeMap map = new TreeMap(new ComparatorUtilBean());
            Enumeration tempKeys = operatorsht.keys();
            while (tempKeys.hasMoreElements()) {
                String tempKey = (String) tempKeys.nextElement();
                ArrayList tempoperators = (ArrayList) operatorsht.get(tempKey);
                map.put(tempKey,tempoperators);
            }
            Iterator iterator = map.keySet().iterator();
            while(iterator.hasNext()) {
                String operatorgroup = (String) iterator.next();
                ArrayList operators = (ArrayList) operatorsht.get(operatorgroup);
            /*---------added by xwj for td2850 end----*/

            /* ------------ xwj for td2104 on 20050802 end------------------*/
                for (int i = 0; i < operators.size(); i++) {
                    showorder++; //xwj for td2104 on 20050802
                    String operatorandtype = (String) operators.get(i);
                    String[] operatorandtypes = Util.TokenizerString2(operatorandtype, "_");
                    String opertor = operatorandtypes[0];
                    //System.out.println(opertor);
                    String opertortype = operatorandtypes[1];
                    int groupdetailid = Util.getIntValue(operatorandtypes[2],-1);
					 //add by liaodong for qc in 2013-11-06 start
                    int typeid= Util.getIntValue(operatorandtypes[3],0);
                    //end
                    //modify by xhheng @20050109 for 流程代理
                    //代理数据检索





                    boolean isbeAgent=false;
                    String agenterId="";


                 /*-----------   xwj td2551  20050808  begin -----------*/
                 String agentCheckSql = " select * from workflow_agentConditionSet where workflowId="+ workflowid +" and bagentuid=" + opertor +
                 " and agenttype = '1'  and isproxydeal='1' " +
                 " and ( ( (endDate = '" + currentdate + "' and (endTime='' or endTime is null))" +
                 " or (endDate = '" + currentdate + "' and endTime > '" + currenttime + "' ) ) " +
                 " or endDate > '" + currentdate + "' or endDate = '' or endDate is null)" +
                 " and ( ( (beginDate = '" + currentdate + "' and (beginTime='' or beginTime is null))" +
                 " or (beginDate = '" + currentdate + "' and beginTime < '" + currenttime + "' ) ) " +
                 " or beginDate < '" + currentdate + "' or beginDate = '' or beginDate is null) order by agentbatch asc  ,id asc ";
                 rs3.execute(agentCheckSql);
                 while(rs3.next()){
                	 String agentid = rs3.getString("agentid");
						String conditionkeyid = rs3.getString("conditionkeyid");
						boolean isagentcond = wfAgentCondition.isagentcondite(""+ requestid, "" + workflowid, "" + opertor,"" + agentid, "" + conditionkeyid);
						 if(isagentcond){
							 isbeAgent=true;
							 agenterId=rs3.getString("agentuid");
							 break;
						 }
                }
                    /* -----------   xwj td2551  20050808  end -----------*/


                    //当符合代理条件时添加代理人





                    String Procpara1="";
					 //add by liaodong for qc80034 in 2013-11-07  start
                    int tempremark=0;
					if (typeid==-3){//抄送（不需提交）





						tempremark=8;
					}
					if (typeid==-4){//抄送（需提交）





						tempremark=9;
					}
                 //end

                    /*-------- xwj for td2104 on 20050802  begin --------- */
                    if(isOldOrNewFlag(requestid)){//老数据, 相对 td2104 之前
                        if(isbeAgent){
							       //add by liaodong for qc80034 in 2013-11-06 start
                                   if(tempremark==8 || tempremark==9){ //抄送的时候





									    //设置被代理人已操作





                                         String Procpara = "" + requestid + flag + opertor + flag + operatorgroup + flag
                                                + workflowid + flag + workflowtype + flag + opertortype + flag + tempremark + flag + -1 +
                                                flag + -1 + flag + "0" + flag + -1+ flag +groupdetailid;
                                                rs3.executeProc("workflow_CurrentOperator_I", Procpara);
                                                //设置代理人





                                                Procpara1 = "" + requestid + flag + agenterId + flag + operatorgroup + flag
                                                + workflowid + flag + workflowtype + flag + opertortype + flag + tempremark + flag + -1 +
                                                flag + -1 + flag + "0" + flag + -1 + flag +groupdetailid;
                                                rs3.executeProc("workflow_CurrentOperator_I", Procpara1);
												operator89List.add(""+opertor); 
								   }else{

									   //设置被代理人已操作





                                         String Procpara = "" + requestid + flag + opertor + flag + operatorgroup + flag
                                                + workflowid + flag + workflowtype + flag + opertortype + flag + "2" + flag + -1 +
                                                flag + -1 + flag + "0" + flag + -1+ flag +groupdetailid;
                                                rs3.executeProc("workflow_CurrentOperator_I", Procpara);
                                                //设置代理人





                                                Procpara1 = "" + requestid + flag + agenterId + flag + operatorgroup + flag
                                                + workflowid + flag + workflowtype + flag + opertortype + flag + "0" + flag + -1 +
                                                flag + -1 + flag + "0" + flag + -1 + flag +groupdetailid;
                                                rs3.executeProc("workflow_CurrentOperator_I", Procpara1);
								       
								   
								   }
                                                
                               }else{
								      //add by liaodong for qc80034 in 2013-11-06 start
                                   if(tempremark==8 || tempremark==9){ //抄送的时候





									                String Procpara = "" + requestid + flag + opertor + flag + operatorgroup + flag
                                                + workflowid + flag + workflowtype + flag + opertortype + flag + tempremark + flag + -1 +
                                                flag + -1 + flag + "0" + flag + -1 + flag +groupdetailid;
                                                rs3.executeProc("workflow_CurrentOperator_I", Procpara);
												operator89List.add(""+opertor);
								   }else{
								                   String Procpara = "" + requestid + flag + opertor + flag + operatorgroup + flag
                                                + workflowid + flag + workflowtype + flag + opertortype + flag + "0" + flag + -1 +
                                                flag + -1 + flag + "0" + flag + -1 + flag +groupdetailid;
                                                rs3.executeProc("workflow_CurrentOperator_I", Procpara);
								   }
                                    
                               }
                    }
                    else{
                                           if(isbeAgent){
											       //add by liaodong for qc80034 in 2013-11-06 start
                                                  if(tempremark==8 || tempremark==9){ //抄送的时候





													     //设置被代理人已操作





														String Procpara = "" + requestid + flag + opertor + flag + operatorgroup + flag
														+ workflowid + flag + workflowtype + flag + opertortype + flag + tempremark + flag + nextnodeid +
														flag + agenterId + flag + "1" + flag + showorder+ flag +groupdetailid;
														rs3.executeProc("workflow_CurrentOperator_I", Procpara);
														//设置代理人





														Procpara1 = "" + requestid + flag + agenterId + flag + operatorgroup + flag
														+ workflowid + flag + workflowtype + flag + opertortype + flag + tempremark + flag + nextnodeid +
														flag + opertor + flag + "2" + flag + showorder+ flag +groupdetailid;
														rs3.executeProc("workflow_CurrentOperator_I", Procpara1);
														operator89List.add(""+opertor);
								                  }else{
													    //设置被代理人已操作





                                                    String Procpara = "" + requestid + flag + opertor + flag + operatorgroup + flag
                                                    + workflowid + flag + workflowtype + flag + opertortype + flag + "2" + flag + nextnodeid +
                                                    flag + agenterId + flag + "1" + flag + showorder+ flag +groupdetailid;
                                                    rs3.executeProc("workflow_CurrentOperator_I", Procpara);
                                                    //设置代理人





                                                    Procpara1 = "" + requestid + flag + agenterId + flag + operatorgroup + flag
                                                    + workflowid + flag + workflowtype + flag + opertortype + flag + "0" + flag + nextnodeid +
                                                    flag + opertor + flag + "2" + flag + showorder+ flag +groupdetailid;
                                                    rs3.executeProc("workflow_CurrentOperator_I", Procpara1);
								                  }
                                                   
                                                }else{
													   //add by liaodong for qc80034 in 2013-11-06 start
                                                  if(tempremark==8 || tempremark==9){ //抄送的时候





													    String Procpara = "" + requestid + flag + opertor + flag + operatorgroup + flag
                                                    + workflowid + flag + workflowtype + flag + opertortype + flag + tempremark + flag + nextnodeid +
                                                    flag + -1 + flag + "0" + flag + showorder+ flag +groupdetailid;
                                                    //System.out.println(Procpara);
                                                    rs3.executeProc("workflow_CurrentOperator_I", Procpara);
													operator89List.add(""+opertor);
												  }else{
												       String Procpara = "" + requestid + flag + opertor + flag + operatorgroup + flag
                                                    + workflowid + flag + workflowtype + flag + opertortype + flag + "0" + flag + nextnodeid +
                                                    flag + -1 + flag + "0" + flag + showorder+ flag +groupdetailid;
                                                    //System.out.println(Procpara);
                                                    rs3.executeProc("workflow_CurrentOperator_I", Procpara);
												  }
                                                   
                                                }

                    }



                    //对代理人判断提醒

                    /*--xwj for td3450 20060111 begin--*/
                    String Procpara = opertor + flag + opertortype + flag + requestid;

                    if (nextnodetype.equals("3")){


                        if(isbeAgent){
                            if(!operatorsWfEnd.contains(agenterId+"_"+opertortype)){
                                //poppupRemindInfoUtil.addPoppupRemindInfo(Integer.parseInt(agenterId),1,opertortype,requestid,requestcominfo.getRequestname(requestid+""));
                       	        Map popmap=new HashMap();
                       	        popmap.put("userid",""+Integer.parseInt(agenterId));
                       	        popmap.put("type","1");
                       	        popmap.put("logintype",""+opertortype);
                       	        popmap.put("requestid",""+requestid);
                       	        popmap.put("requestname",""+Util.formatMultiLang(requestcominfo.getRequestname(requestid+"")));
                       	        popmap.put("workflowid",""+workflowid);
                       	        popmap.put("creater","");
                    		    poppuplist.add(popmap);
                                operatorsWfEnd.add(agenterId+"_"+opertortype);
                            }
                        }else{
                            if(!operatorsWfEnd.contains(opertor+"_"+opertortype)){
                                //poppupRemindInfoUtil.addPoppupRemindInfo(Integer.parseInt(opertor),1,opertortype,requestid,requestcominfo.getRequestname(requestid+""));
                      	        Map popmap=new HashMap();
                       	        popmap.put("userid",""+Integer.parseInt(opertor));
                       	        popmap.put("type","1");
                       	        popmap.put("logintype",""+opertortype);
                       	        popmap.put("requestid",""+requestid);
                       	        popmap.put("requestname",""+Util.formatMultiLang(requestcominfo.getRequestname(requestid+"")));
                       	        popmap.put("workflowid",""+workflowid);
                       	        popmap.put("creater","");
                    		    poppuplist.add(popmap);
                                operatorsWfEnd.add(opertor+"_"+opertortype);
                          }
                        }
                    }
                    else{
                       if(isbeAgent){
                            if(!operatorsWfNew.contains(agenterId+"_"+opertortype)){
                            	//poppupRemindInfoUtil.addPoppupRemindInfo(Integer.parseInt(agenterId),0,opertortype,requestid,requestcominfo.getRequestname(requestid+""));
                                Map popmap=new HashMap();
                       	        popmap.put("userid",""+Integer.parseInt(agenterId));
                       	        popmap.put("type","0");
                       	        popmap.put("logintype",""+opertortype);
                       	        popmap.put("requestid",""+requestid);
                       	        popmap.put("requestname",""+Util.formatMultiLang(requestcominfo.getRequestname(requestid+"")));
                       	        popmap.put("workflowid",""+workflowid);
                       	        popmap.put("creater","");
                    		    poppuplist.add(popmap);
                            operatorsWfNew.add(agenterId+"_"+opertortype);
                            }

                        }else{

                            if(!operatorsWfNew.contains(opertor+"_"+opertortype)){
                            	//poppupRemindInfoUtil.addPoppupRemindInfo(Integer.parseInt(opertor),0,opertortype,requestid,requestcominfo.getRequestname(requestid+""));
                            	Map popmap=new HashMap();
                          	    popmap.put("userid",""+Integer.parseInt(opertor));
                          	    popmap.put("type","0");
                          	    popmap.put("logintype",""+opertortype);
                          	    popmap.put("requestid",""+requestid);
                          	    popmap.put("requestname",""+Util.formatMultiLang(requestcominfo.getRequestname(requestid+"")));
                          	    popmap.put("workflowid",""+workflowid);
                          	    popmap.put("creater","");
                          	    poppuplist.add(popmap);
                            operatorsWfNew.add(opertor+"_"+opertortype);
                            }
                        }
                    }
                }
            }
            //操作人更新结束

            try {
				boolean isautoApprove = wfautoApproveUtil.isAutoApprove(rm, rst, nodeInfoCache,poppuplist,nextnodeattr);
				if(!isautoApprove){
				   poppupRemindInfoUtil.insertPoppupRemindInfo(poppuplist);
				}
				rst.commit();
			} catch (Exception e) {
				rst.rollback();
				e.printStackTrace();
			}



            //更新当前节点表





            if(innodeids.equals("")||innodeids.equals("0")) innodeids=nodeid+"";
            rs3.executeSql("delete from workflow_nownode where nownodeid in("+innodeids+") and requestid="+requestid);
            rs3.executeSql("insert into workflow_nownode(requestid,nownodeid,nownodetype,nownodeattribute) values("+requestid+","+nextnodeid+","+nextnodetype+","+nextnodeattr+")");
        }
     }

    /**
     * 插入指定对象操作人





     * @param opertorlist
     * @param requestid
     * @param workflowid
     * @param workflowtype
     * @param nextnodeid
     */
    public void setOperator(ArrayList opertorlist,int requestid,int workflowid,int workflowtype,int nextnodeid)
     {
    	 wfAgentCondition wfAgentCondition=new wfAgentCondition();
         Calendar today = Calendar.getInstance();
        String currentdate = Util.add0(today.get(Calendar.YEAR), 4) + "-" +
                Util.add0(today.get(Calendar.MONTH) + 1, 2) + "-" +
                Util.add0(today.get(Calendar.DAY_OF_MONTH), 2);

        String currenttime = Util.add0(today.get(Calendar.HOUR_OF_DAY), 2) + ":" +
                Util.add0(today.get(Calendar.MINUTE), 2) + ":" +
                Util.add0(today.get(Calendar.SECOND), 2);
         //获得数据库服务器当前时间
        String sql="";
        if(rs5.getDBType().equals("oracle")){
            sql="select to_char(sysdate,'yyyy-mm-dd') currentdate,to_char(sysdate,'hh24:mi:ss') currenttime from dual";
        }else{
            sql="select convert(char(10),getdate(),20) currentdate,convert(char(8),getdate(),108) currenttime";
        }
        rs5.executeSql(sql);
        if(rs5.next()){
           currentdate=rs5.getString("currentdate");
           currenttime=rs5.getString("currenttime");
        }
         ArrayList operatorsWfNew = new ArrayList();
         char flag=Util.getSeparator();
         int showorder = 0;
         String operatorgroup = "0";
         for(int i=0;i<opertorlist.size();i++){
             showorder++;
             String opertor = (String)opertorlist.get(i);
             String opertortype = "0";
             int groupdetailid = -1;
             //modify by xhheng @20050109 for 流程代理
             //代理数据检索





             boolean isbeAgent=false;
             String agenterId="";


                 /*-----------   xwj td2551  20050808  begin -----------*/
                 String agentCheckSql = " select * from workflow_agentConditionSet where workflowId="+ workflowid +" and bagentuid=" + opertor +
                 " and agenttype = '1' and isproxydeal='1'  " +
                 " and ( ( (endDate = '" + currentdate + "' and (endTime='' or endTime is null))" +
                 " or (endDate = '" + currentdate + "' and endTime > '" + currenttime + "' ) ) " +
                 " or endDate > '" + currentdate + "' or endDate = '' or endDate is null)" +
                 " and ( ( (beginDate = '" + currentdate + "' and (beginTime='' or beginTime is null))" +
                 " or (beginDate = '" + currentdate + "' and beginTime < '" + currenttime + "' ) ) " +
                 " or beginDate < '" + currentdate + "' or beginDate = '' or beginDate is null) order by agentbatch asc  ,id asc ";

                 rs3.execute(agentCheckSql);
                 while(rs3.next()){
                 	String agentid = rs3.getString("agentid");
						String conditionkeyid = rs3.getString("conditionkeyid");
						//妫€鏌ュ綋鍓嶆祦绋嬩笅鐨勪唬鐞嗘槸鍚︽敮鎸佹壒娆℃潯浠躲€愬紑鍚祦绋嬩腑鐨勪唬鐞嗐€佸凡缁忔槸娴佽浆涓殑銆佹壒娆℃潯浠舵弧瓒炽€?
						boolean isagentcond = wfAgentCondition.isagentcondite(""+ requestid, "" + workflowid, "" + opertor,"" + agentid, "" + conditionkeyid);
						 if(isagentcond){
							 isbeAgent=true;
							 agenterId=rs3.getString("agentuid");
							 break;
						 }
                     
             }
                    /* -----------   xwj td2551  20050808  end -----------*/


                    //当符合代理条件时添加代理人





                    String Procpara1="";

                    /*-------- xwj for td2104 on 20050802  begin --------- */
                    if(isOldOrNewFlag(requestid)){//老数据, 相对 td2104 之前
                        if(isbeAgent){
                                                //设置被代理人已操作





                                                String Procpara = "" + requestid + flag + opertor + flag + operatorgroup + flag
                                                + workflowid + flag + workflowtype + flag + opertortype + flag + "2" + flag + -1 +
                                                flag + -1 + flag + "0" + flag + -1+ flag +groupdetailid;
                                                rs3.executeProc("workflow_CurrentOperator_I", Procpara);
                                                //设置代理人 isremark=5为干扰流转





                                                Procpara1 = "" + requestid + flag + agenterId + flag + operatorgroup + flag
                                                + workflowid + flag + workflowtype + flag + opertortype + flag + "5" + flag + -1 +
                                                flag + -1 + flag + "0" + flag + -1 + flag +groupdetailid;
                                                rs3.executeProc("workflow_CurrentOperator_I", Procpara1);
                                            }else{
                                                String Procpara = "" + requestid + flag + opertor + flag + operatorgroup + flag
                                                + workflowid + flag + workflowtype + flag + opertortype + flag + "5" + flag + -1 +
                                                flag + -1 + flag + "0" + flag + -1 + flag +groupdetailid;
                                                rs3.executeProc("workflow_CurrentOperator_I", Procpara);
                                            }
                    }
                    else{
                                                if(isbeAgent){
                                                    //设置被代理人已操作





                                                    String Procpara = "" + requestid + flag + opertor + flag + operatorgroup + flag
                                                    + workflowid + flag + workflowtype + flag + opertortype + flag + "2" + flag + nextnodeid +
                                                    flag + agenterId + flag + "1" + flag + showorder+ flag +groupdetailid;
                                                    rs3.executeProc("workflow_CurrentOperator_I", Procpara);
                                                    //设置代理人





                                                    Procpara1 = "" + requestid + flag + agenterId + flag + operatorgroup + flag
                                                    + workflowid + flag + workflowtype + flag + opertortype + flag + "5" + flag + nextnodeid +
                                                    flag + opertor + flag + "2" + flag + showorder+ flag +groupdetailid;
                                                    rs3.executeProc("workflow_CurrentOperator_I", Procpara1);
                                                }else{
                                                    String Procpara = "" + requestid + flag + opertor + flag + operatorgroup + flag
                                                    + workflowid + flag + workflowtype + flag + opertortype + flag + "5" + flag + nextnodeid +
                                                    flag + -1 + flag + "0" + flag + showorder+ flag +groupdetailid;
                                                    //System.out.println(Procpara);
                                                    rs3.executeProc("workflow_CurrentOperator_I", Procpara);
                                                }

                    }

                    /*-------- xwj for td2104 on 20050802  end ---------*/

                    //将已查看操作人的查看状态置为（-1：新提交未查看）
                    //TD4294  删除workflow_currentoperator表中orderdate、ordertime列 fanggsh begin
                    //rs3.executeSql("update workflow_currentoperator set viewtype =-1,orderdate='" + currentdate + "' ,ordertime='" + currenttime + "'  where requestid=" + requestid + " and userid<>" + opertor + " and viewtype=-2");
                    //rs3.executeSql("update workflow_currentoperator set viewtype =-1   where requestid=" + requestid + " and userid=" + opertor + " and viewtype=-2");
                    //TD4294  删除workflow_currentoperator表中orderdate、ordertime列 fanggsh end

                    //将自己的查看状态置为（-2：已提交已查看）
                    //by ben 2006-03-27加上nodeid的条件限制后一个节点有相同于当前操作人时只设置当前的节点





                    //rs3.executeSql("update workflow_currentoperator set viewtype =-2 where requestid=" + requestid + "  and userid=" + opertor + " and usertype = "+opertortype+" and viewtype<>-2");
                    /*-------- xwj for td2104 on 20050802  end ---------*/

                    //对代理人判断提醒

                    /*--xwj for td3450 20060111 begin--*/
                    String Procpara = opertor + flag + opertortype + flag + requestid;

                       if(isbeAgent){
                            if(!operatorsWfNew.contains(agenterId+"_"+opertortype)){
                            poppupRemindInfoUtil.addPoppupRemindInfo(Integer.parseInt(agenterId),0,opertortype,requestid,requestcominfo.getRequestname(requestid+""));
                            operatorsWfNew.add(agenterId+"_"+opertortype);
                            }

                        }else{

                            if(!operatorsWfNew.contains(opertor+"_"+opertortype)){
                            poppupRemindInfoUtil.addPoppupRemindInfo(Integer.parseInt(opertor),0,opertortype,requestid,requestcominfo.getRequestname(requestid+""));
                            operatorsWfNew.add(opertor+"_"+opertortype);
                            }
                        }
                }
            //操作人更新结束





			
			RecordSetTrans rst = new RecordSetTrans();
			try {
				String nextnodetype = "";
				rs3.executeSql("select nodetype from workflow_flownode where workflowid="+workflowid+" and nodeid="+nextnodeid);
		        if(rs3.next()){
		        	nextnodetype=rs3.getString("nodetype");
		        }
				
				
				rst.setAutoCommit(false);
				String src = "submit";
				SendMsgAndMail sendMsgAndMail = new SendMsgAndMail();
				sendMsgAndMail.setIsIntervene("1");
				sendMsgAndMail.setInterveneOperators(opertorlist);
				//发送短信





				sendMsgAndMail.sendMsg(rst,requestid,nextnodeid,user,src,nextnodetype);
				// 邮件提醒
				sendMsgAndMail.sendMail(rst,workflowid,requestid,nextnodeid,null,null,false,src,nextnodetype,user);
				
				rst.commit();
				System.out.println("超时干预发送短信====================="+requestid);
			} catch (Exception e) {
				rst.rollback();
				System.out.println("超时干预发送短信====================="+e.getMessage());
				writeLog("超时干预短信提醒："+e);
			}
     }

    /*
	 * @author xwj  20050802
	 *判断当前流程是否为老数据(相对于 td2104 以前)
	 */
     public boolean isOldOrNewFlag(int requestid){
        boolean isOldWf = false;
        RecordSet  rs_ = new RecordSet();
        rs_.executeSql("select nodeid from workflow_currentoperator where requestid = " + requestid);
        while(rs_.next()){
            if(rs_.getString("nodeid") == null || "".equals(rs_.getString("nodeid")) || "-1".equals(rs_.getString("nodeid"))){
             isOldWf = true;
            }
        }
        return isOldWf;
     }
	private void writeWFLog(int requestid,int workflowid,int nodeid,int userid,int usertype,int nextnodeid,String currentdate,String currenttime,String remark,String logtype,boolean canflowtonextnode,int nextnodeattr){
		writeWFLog(requestid,workflowid,nodeid,userid,usertype,-1,-1,nextnodeid,currentdate,currenttime,remark,logtype,canflowtonextnode,nextnodeattr);
	}
    /**
     * 日志记录
     * @param requestid
     * @param workflowid
     * @param nodeid
     * @param userid
     * @param usertype
     * @param nextnodeid
     * @param currentdate
     * @param currenttime
     * @param remark
     * @param logtype
     * @param canflowtonextnode
     * @param nextnodeattr
     */
    private void writeWFLog(int requestid,int workflowid,int nodeid,int userid,int usertype,int agenttype,int agentorbyagentid,int nextnodeid,String currentdate,String currenttime,String remark,String logtype,boolean canflowtonextnode,int nextnodeattr){
        String clientip = "127.0.0.1";
        char flag=Util.getSeparator();
		String personStr = "";


		/*  ----------------       xwj for td2104 on 20050802           B E G I N     ------------------*/
		if(isOldOrNewFlag(requestid)){//老数据, 相对 td2104 之前
		   //add by liaodong for qc80034 in 2013-11-7 start
			if("t".equals(logtype)){
			     //add by liaodong for qc80034 in 2013-11-7 start
				for(int i=0;i<operator89List.size();i++){
					personStr += Util.toScreen(resource.getResourcename((String)operator89List.get(i)),usertype)+",";
				}
				//end
				rs3.executeSql("select operator  from workflow_requestLog where workflowid ="+workflowid+"  and requestid="+requestid+" and logtype != 't' and nodeid = "+nodeid+" order by operatedate,operatetime  desc ");
				if(rs3.next()){
					userid = rs3.getInt("operator");
				}
			}else{
			    if(logtype.equals("7")){
                    rs3.executeSql("select userid,usertype from workflow_currentoperator where isremark = '5' and requestid = " + requestid);
                }else{
                    rs3.executeSql("select userid,usertype from workflow_currentoperator where isremark = '0' and requestid = " + requestid);
                 }
                  while(rs3.next()){
					  // add by liaodong for qc80034 in 2013-11-06  start
					   if(!isCopyTo(rs3,operator89List)){ 
					         if("0".equals(rs3.getString("usertype"))){
						          personStr	+= Util.toScreen(resource.getResourcename(rs3.getString("userid")),user.getLanguage()) + ",";
						     } else{
						       personStr	+= Util.toScreen(crminfo.getCustomerInfoname(rs3.getString("userid")),user.getLanguage()) + ",";
						     }
					   }
						 
				 }
			}

            


						 String Procpara = "" + requestid + flag + workflowid + flag + nodeid + flag + logtype + flag
						   + currentdate + flag + currenttime + flag + userid + flag + remark + flag
						   + clientip + flag + usertype + flag + nextnodeid + flag + personStr.trim()+ flag + -1 + flag + "0" + flag + -1+flag+""+flag+"0"+ flag + ""+flag+"";
						 rs3.executeProc("workflow_RequestLog_Insert", Procpara);
		}
		else{
										String tempSQL = "";
										//int agentorbyagentid = -1;
										//int agenttype = 0;
										int showorder = 1;
								//add by liaodong for qc80034 in 2013-11-7 start
								if("t".equals(logtype)){
								    	//add by liaodong for qc80034 in 2013-11-7 start
										for(int i=0;i<operator89List.size();i++){
											personStr += Util.toScreen(resource.getResourcename((String)operator89List.get(i)),usertype)+",";
										}
										//end
										rs3.executeSql("select operator  from workflow_requestLog where workflowid ="+workflowid+"  and requestid="+requestid+" and logtype != 't' and nodeid = "+nodeid+" order by operatedate,operatetime  desc ");
										if(rs3.next()){
											userid = rs3.getInt("operator");
										}
								}else{
                                      if(logtype.equals("7")){
                                            rs3.executeSql("select userid,usertype,agentorbyagentid, agenttype from workflow_currentoperator where isremark='5' and requestid = " + requestid  + " and nodeid="+nextnodeid+" order by showorder asc");
                                        }else{
                                           rs3.executeSql("select userid,usertype,agentorbyagentid, agenttype from workflow_currentoperator where isremark in ('0','4','f') and requestid = " + requestid  + " and nodeid="+nextnodeid+" order by showorder asc");
                                        }
                                        //System.out.println("select userid,usertype,agentorbyagentid, agenttype from workflow_currentoperator where isremark in ('0','4') and requestid = " + requestid  + " order by showorder asc");
                                        while(rs3.next()){
											   // add by liaodong for qc80034 in 2013-11-06  start
					                          if(!isCopyTo(rs3,operator89List)){ 
												  if("0".equals(rs3.getString("usertype"))){
													if(rs3.getInt("agenttype") == 0){
														String tempPersonStr = Util.toScreen(resource.getResourcename(rs3.getString("userid")),user.getLanguage());
														if(personStr.indexOf(","+tempPersonStr+",") == -1 && personStr.indexOf(tempPersonStr+",") == -1){
														personStr	+= tempPersonStr + ",";
														}
													 }
													  else if(rs3.getInt("agenttype") == 2){
														String tempPersonStr = Util.toScreen(resource.getResourcename(rs3.getString("agentorbyagentid")),user.getLanguage()) + "->" + Util.toScreen(resource.getResourcename(rs3.getString("userid")),user.getLanguage());
														if(personStr.indexOf(","+tempPersonStr+",") == -1 && personStr.indexOf(tempPersonStr+",") == -1){
														personStr	+= tempPersonStr + ",";
														}
													  }
													 else{
													    }
													}else{
														String tempPersonStr = Util.toScreen(crminfo.getCustomerInfoname(rs3.getString("userid")),user.getLanguage());
														if(personStr.indexOf(","+tempPersonStr+",") == -1 && personStr.indexOf(tempPersonStr+",") == -1){
															personStr	+= tempPersonStr + ",";
														}
													}

					                           }
                                       
										}
								}

                                       
							/*
							tempSQL = "select agentorbyagentid, agenttype, showorder from workflow_currentoperator where nodeid = " + nodeid +
							" and requestid = " + requestid + " and userid = " + userid + " and nodeid="+nextnodeid+" order by showorder asc";
							rs3.executeSql(tempSQL);
							if(rs3.next()){
							   agentorbyagentid = rs3.getInt("agentorbyagentid");
							   agenttype = rs3.getInt("agenttype");
							   showorder = rs3.getInt("showorder");
							}
							*/
                            if(!canflowtonextnode&&(nextnodeattr==3||nextnodeattr==4)){
                                personStr= SystemEnv.getHtmlLabelName(21399,user.getLanguage())+",";
                            }
//							String Procpara = "" + requestid + flag + workflowid + flag + nodeid + flag + logtype + flag
//											+ currentdate + flag + currenttime + flag + userid + flag + remark + flag
//											+ clientip + flag + usertype + flag + nextnodeid + flag + personStr.trim() + flag + agentorbyagentid + flag + agenttype + flag + showorder+flag+""+flag+"0"+ flag + ""+flag+"";

//							rs3.executeProc("workflow_RequestLog_Insert", Procpara);
							String Procpara = "" + requestid + flag + workflowid + flag + nodeid + flag + logtype + flag + currentdate + flag + currenttime + flag + userid + flag + clientip + flag + usertype + flag + nextnodeid + flag + personStr.trim() + flag + agentorbyagentid + flag + agenttype + flag + showorder + flag + ""          + flag + "0" +          flag + "" + flag + "" + flag + "" + flag + "" + flag + "" + flag + "" + flag + "";
                            new RequestManager().execRequestlog(Procpara, rs3, flag, remark);

		}

		/*  ----------------   xwj for td2104 on 20050802   E N D  ------------------*/

    }

	     //add by liaodong for qc80034 in 2013-11-6 start 
	public boolean isCopyTo(RecordSet rs3,ArrayList operator89List) {
		//判断是否是抄送的数据
		   boolean isCopyTo =false;
		   String copyToUserId=rs3.getString("userid");
		   for(int i=0;i<operator89List.size();i++){
			 String cUserId =  (String) operator89List.get(i);
			 if(copyToUserId.equals(cUserId)){
				 isCopyTo = true;
				 break;
			 }
		   }
		   return isCopyTo;
	}
    
    private int updateManagerField(int requestID, int formid, int isbill, int userID) {
    	int result = 0;
    	RecordSet rs = new RecordSet();
    	String formfieldsql = "";
		if(isbill == 1){
			formfieldsql = "select fieldname from workflow_billfield where billid="+formid+" order by dsporder";
		}else{
			formfieldsql = "select fieldname from workflow_formdict where id IN (select fieldid from workflow_formfield where formid=" + formid + " and (isdetail<>'1' or isdetail is null))" ;
		}
		
		rs.executeSql(formfieldsql);
		while(rs.next()){
			String fieldname = rs.getString("fieldname");
			if ("manager".equals(fieldname)) {
				RecordSet sltRs = new RecordSet();
				String billtablename = "";
				int managerID = 0;
				
				String mgrSql = "select managerid from hrmresource where id=" + userID; 
				
				sltRs.executeSql(mgrSql);
				if (sltRs.next()) {
					managerID = Util.getIntValue(sltRs.getString("managerid"), 0);
				}
				//System.out.println("managerID=" + managerID);
				
				String sltmgsSql = "";
				String updateSql = "";
				if(isbill == 1){
					sltRs.executeSql("select tablename from workflow_bill where id = " + formid); // 查询工作流单据表的信息





					if (sltRs.next()) {
						billtablename = sltRs.getString("tablename");          // 获得单据的主表





						}
					sltRs.executeSql("select * from workflow_billfield where fieldname='manager' and billid = " + formid); // 查询工作流单据表是否存在manager字段
					if (sltRs.next()) {
						sltmgsSql = "select manager from " + billtablename + " where requestid=" + requestID;
						updateSql = "update " + billtablename + " set manager=" + managerID + " where requestid=" + requestID;
					}
				} else {
					sltmgsSql = "select manager from workflow_form where requestid=" + requestID;
					updateSql = "update workflow_form set manager=" + managerID + " where requestid=" + requestID;;
				}
				
				sltRs.execute(sltmgsSql);
				if (sltRs.next()) {
					result = Util.getIntValue(sltRs.getString("manager"), 0);
				}
				sltRs.executeSql(updateSql);
				break;
			}
		}
    	return result;
    }

    private boolean rollbackUpdatedManagerField(int requestID, int formid, int isbill, int mgrID) {
    	
    	if (mgrID == 0) return false;
    	
    	boolean result = false;
			RecordSet sltRs = new RecordSet();
			String billtablename = "";
			
			String updateSql = "";
			if(isbill == 1){
				sltRs.executeSql("select tablename from workflow_bill where id = " + formid); // 查询工作流单据表的信息





				if (sltRs.next()) {
					billtablename = sltRs.getString("tablename");          // 获得单据的主表





					}
					sltRs.executeSql("select * from workflow_billfield where fieldname='manager' and billid = " + formid); // 查询工作流单据表是否存在manager字段
					if (sltRs.next()) {
					updateSql = "update " + billtablename + " set manager=" + mgrID + " where requestid=" + requestID;
				}
			} else {
				updateSql = "update workflow_form set manager=" + mgrID + " where requestid=" + requestID;;
			}
			
			sltRs.executeSql(updateSql);
    	return true;
    }
    
    
    /**
     * 流程退回





     * @param requestid
     * @param userid
     * @param remark
     * @param src
     * @param needback
     * @return
     */
    private boolean FlowNode(int requestid, int userid, String remark,String needback,int destnodeid) {
        boolean flowflag = false;
        try {
        	String src = "reject";
            RecordSet rs = new RecordSet();
            RecordSet rs1 = new RecordSet();
            RecordSet rSet6 = new RecordSet();
						RecordSet rSet7 = new RecordSet();
            WorkFlowInit wfi = new WorkFlowInit();
            WFLinkInfo wfli = new WFLinkInfo();
            rs.executeProc("workflow_Requestbase_SByID", requestid + "");
            if (rs.next()) {
                String requestname = Util.null2String(rs.getString("requestname"));
                String requestlevel = Util.null2String(rs.getString("requestlevel"));
                int workflowid = Util.getIntValue(rs.getString("workflowid"), 0);
                int nodeid = wfli.getCurrentNodeid(requestid, userid, 1);               //节点id
                String nodetype = wfli.getNodeType(nodeid);
                int currentnodeid = Util.getIntValue(rs.getString("currentnodeid"), 0);
                if (nodeid < 1) nodeid = currentnodeid;
                String currentnodetype = Util.null2String(rs.getString("currentnodetype"));
                if (nodetype.equals("")) nodetype = currentnodetype;
                rs.executeSql("select * from workflow_base where id=" + workflowid);
                if (rs.next()) {
                    String workflowtype = Util.null2String(rs.getString("workflowtype"));
                    int formid = Util.getIntValue(rs.getString("formid"));
                    int isbill = Util.getIntValue(rs.getString("isbill"));
                    String messageType = Util.null2String(rs.getString("messageType"));
                    int billid=-1;
                    rs.executeSql("select billid from workflow_form where requestid=" + requestid);
                    if (rs.next()) {
                        billid=Util.getIntValue(rs.getString("billid"));
                    }
                    rs.executeSql("select id,isremark,isreminded,preisremark,groupdetailid,nodeid from workflow_currentoperator where requestid="+requestid+" and userid="+userid+" and usertype=0 and isremark in('0','1','7','8','9') order by id");
                    if(rs.next()){
                        int isremark=rs.getInt("isremark");
                        //转发1、抄送(不需提交)9：抄送(需提交)
                        if(isremark==1||isremark==8||isremark==9){
							
                        }else{
	                        RequestManager rm = new RequestManager();
	                        rm.setUser(wfi.getUser(userid));
	                        rm.setSrc(src);
	                        rm.setIscreate("");
	                        rm.setRequestid(requestid);
	                        rm.setWorkflowid(workflowid);
	                        rm.setWorkflowtype(workflowtype);
	                        rm.setIsremark(0);
	                        rm.setFormid(formid);
	                        rm.setIsbill(isbill);
	                        rm.setBillid(billid);
	                        rm.setNodeid(nodeid);
	                        rm.setNodetype(nodetype);
	                        rm.setRequestname(requestname);
	                        rm.setRequestlevel(requestlevel);
	                        rm.setRemark(remark);
	                        rm.setMessageType(messageType);
	                        rm.setNeedwfback(needback);
	                        rm.setRejectToNodeid(destnodeid);
	                        /*
							 * 处理节点后附加操作





							 */
							RequestCheckAddinRules requestCheckAddinRules = new RequestCheckAddinRules();
							requestCheckAddinRules.resetParameter();
							//add by cyril on 2008-07-28 for td:8835 事务无法开启查询,只能传入
							requestCheckAddinRules.setTrack(false);
							requestCheckAddinRules.setStart(false);
							requestCheckAddinRules.setNodeid(nodeid);
							//end by cyril on 2008-07-28 for td:8835
							requestCheckAddinRules.setRequestid(requestid);
							requestCheckAddinRules.setWorkflowid(workflowid);
							requestCheckAddinRules.setObjid(nodeid);
							requestCheckAddinRules.setObjtype(1);               // 1: 节点自动赋值 0 :出口自动赋值





							requestCheckAddinRules.setIsbill(isbill);
							requestCheckAddinRules.setFormid(formid);
							requestCheckAddinRules.setIspreadd("0");//xwj for td3130 20051123
							requestCheckAddinRules.setRequestManager(rm);
							requestCheckAddinRules.setUser(wfi.getUser(userid));
							requestCheckAddinRules.checkAddinRules();
							
							//处理特殊字段manager
						ResourceComInfo rci = new ResourceComInfo();
					    int managerfieldid=-1;
					    String manager = "";
					    String billtablename = "";
					    //表单
					    if(isbill==0){
					    	rSet6.executeSql("select b.id from workflow_formfield a,workflow_formdict b where a.fieldid=b.id and a.isdetail is null and a.formid="+formid+" and b.fieldname='manager'");
					        if(rSet6.next()){
					        	managerfieldid=Util.getIntValue(rSet6.getString("id"));
					        }
					    }
					    //单据
					    if(isbill==1){
					    	rSet6.executeSql("select tablename from workflow_bill where id = " + formid); // 查询工作流单据表的信息





							if(rSet6.next()){
								billtablename = rSet6.getString("tablename");          // 获得单据的主表





							}
					    	rSet6.executeSql("select id from workflow_billfield where billid="+formid+" and viewtype=0 and fieldname='manager'");
					        if(rSet6.next()){
					            managerfieldid=Util.getIntValue(rSet6.getString("id"));
					        }
					    }
					    if(managerfieldid>0){
					    	String beagenter=""+userid;
					    	//获得被代理人
					    	rSet6.executeSql("select agentorbyagentid from workflow_currentoperator where usertype=0 and isremark='0' and requestid="+requestid+" and userid="+beagenter+" and nodeid="+nodeid+" order by id desc");
					    	if(rSet6.next()){
					    		int tembeagenter=rSet6.getInt(1);
					    		if(tembeagenter>0) beagenter=""+tembeagenter;
					    	}
					    	manager = rci.getManagerID(beagenter);
					 		if (manager!=null&&!"".equals(manager)) {
								if (isbill == 1 ) {
									if(billtablename!=null&&!"".equals(billtablename))
										rSet6.executeSql(" update " + billtablename + " set manager = "+manager+" where id = " + billid);
								} else {
									rSet6.executeSql("update workflow_form set manager = "+manager+" where requestid=" + requestid);
								}
							}
					    }
					    
					    BillBgOperation billBgOperation = null;
					    if (isbill == 1 && formid > 0) {
					    	billBgOperation = getBillBgOperation(rm);
					    }
					    
					    if(billBgOperation != null) {
					    	billBgOperation.billDataEdit();
                        }
					    //超时不记录日志
					    rm.setMakeOperateLog(false);
                        flowflag = rm.flowNextNode();
                        
                        /*
                        RecordSetTrans rst = new RecordSetTrans();
            			try {
            				String nextnodetype = "";
            				rSet6.executeSql("select nodetype from workflow_flownode where workflowid="+workflowid+" and nodeid="+destnodeid);
            		        if(rSet6.next()){
            		        	nextnodetype=rSet6.getString("nodetype");
            		        }
            				rst.setAutoCommit(false);
            				src = "reject";
            				SendMsgAndMail sendMsgAndMail = new SendMsgAndMail();
            				//发送短信





            				sendMsgAndMail.sendMsg(rst,requestid,destnodeid,user,src,nextnodetype);
            				// 邮件提醒
            				sendMsgAndMail.sendMail(rst,workflowid,requestid,destnodeid,null,null,false,src,nextnodetype,user);
            				
            				rst.commit();
            				System.out.println("退回至目标节点提醒====================="+requestid);
            			} catch (Exception e) {
            				rst.rollback();
            				System.out.println("退回至目标节点提醒====================="+e.getMessage());
            				writeLog("退回至目标节点提醒："+e);
            			}
            			*/
                        
                        if(billBgOperation != null) {
                        	billBgOperation.setFlowStatus(flowflag);
                        	flowflag = billBgOperation.billExtOperation();
                        }    
						    
                        }
                    }
                }
            }
            
        } catch (Exception e) {
        	flowflag = false;
            log.debug(e.getMessage());
        }
        return flowflag;
    }
    
    private BillBgOperation getBillBgOperation(RequestManager rm) {
    	BillBgOperation billBgOperation = null;
    	String operationpage = "";
		
		try {
			RecordSet rs = new RecordSet();
			int formid = rm.getFormid();
			
			rs.executeProc("bill_includepages_SelectByID",formid+"");
			if(rs.next()) {
		        operationpage = Util.null2String(rs.getString("operationpage")).trim();
		        if (operationpage.indexOf(".jsp") >= 0) {
		        	operationpage = operationpage.substring(0, operationpage.indexOf(".jsp"));
		        } else {
		        	operationpage = null;
		        }
		    }
			
			if (operationpage != null && !"".equals(operationpage)) {
				operationpage = "weaver.soa.workflow.bill."+operationpage;
				Class operationClass = Class.forName(operationpage);
				billBgOperation = (BillBgOperation)operationClass.newInstance();
				billBgOperation.setRequestManager(rm);
			}
		}catch (Exception e) {
			log.debug(e.getMessage());
			return null;
		}
		
    	return billBgOperation;
    }
    
    
    public User getUser(int userid){
    	User user = new User();
    	
    	RecordSet rs = new RecordSet();
    	String sql = "select * from HrmResource where id="+userid+" union select * from HrmResource where id="+userid;
    	rs.executeSql(sql);
        rs.next();
        user.setUid(rs.getInt("id"));
        user.setFirstname(rs.getString("firstname"));
        user.setLastname(rs.getString("lastname"));
        user.setAliasname(rs.getString("aliasname"));
        user.setTitle(rs.getString("title"));
        user.setTitlelocation(rs.getString("titlelocation"));
        user.setSex(rs.getString("sex"));
        String languageidweaver = rs.getString("systemlanguage");
        user.setLanguage(Util.getIntValue(languageidweaver, 0));
        user.setTelephone(rs.getString("telephone"));
        user.setMobile(rs.getString("mobile"));
        user.setMobilecall(rs.getString("mobilecall"));
        user.setEmail(rs.getString("email"));
        user.setCountryid(rs.getString("countryid"));
        user.setLocationid(rs.getString("locationid"));
        user.setResourcetype(rs.getString("resourcetype"));
        user.setContractdate(rs.getString("contractdate"));
        user.setJobtitle(rs.getString("jobtitle"));
        user.setJobgroup(rs.getString("jobgroup"));
        user.setJobactivity(rs.getString("jobactivity"));
        user.setJoblevel(rs.getString("joblevel"));
        user.setSeclevel(rs.getString("seclevel"));
        user.setUserDepartment(Util.getIntValue(rs.getString("departmentid"), 0));
        user.setUserSubCompany1(Util.getIntValue(rs.getString("subcompanyid1"), 0));
        user.setUserSubCompany2(Util.getIntValue(rs.getString("subcompanyid2"), 0));
        user.setUserSubCompany3(Util.getIntValue(rs.getString("subcompanyid3"), 0));
        user.setUserSubCompany4(Util.getIntValue(rs.getString("subcompanyid4"), 0));
        user.setManagerid(rs.getString("managerid"));
        user.setAssistantid(rs.getString("assistantid"));
        user.setPurchaselimit(rs.getString("purchaselimit"));
        user.setCurrencyid(rs.getString("currencyid"));
        user.setLogintype("1");
        user.setAccount(rs.getString("account"));
        
        return user;
    	
    }
    
    //当前操作日志的节点，是否是流程的当前节点
    private boolean isCurrentNode(int requestid,int nodeid){
    	RecordSet rslog = new RecordSet();
    	WFLinkInfo wflinkinfo = new WFLinkInfo();
    	String rsql = "select currentnodeid,currentnodetype from workflow_requestbase where requestid = " + requestid;
		rslog.executeSql(rsql);
		String curnodeid = "";
		String curnodetype = "";
		if(rslog.next()){
			curnodeid = rslog.getString("currentnodeid");
			curnodetype = rslog.getString("currentnodetype");
		}
		
		int nodeattr = wflinkinfo.getNodeAttribute(Util.getIntValue(curnodeid,-1));
        Set<String> branchNodeSet = new HashSet<String>();
        if(nodeattr == 2){   //分支中间节点
        	String branchnodes = "";
        	branchnodes = wflinkinfo.getNowNodeids(requestid);
        	if(!"".equals(branchnodes)){
        		String [] strs = branchnodes.split(",");
        		for(int k = 0; k < strs.length; k++){
        			String nodestr = strs[k];
        			if(!"-1".equals(nodestr)){
        				branchNodeSet.add(nodestr);
        			}
        		}
        	}
        }
        
        int currentNodeId = Util.getIntValue(curnodeid, -1);
        if(((currentNodeId != -1 && currentNodeId == nodeid) || branchNodeSet.contains(curnodeid)) && !"3".equals(curnodetype)){
        	return true;
        }
        return false;
    }
 
    public static void main(String[] args) {
		Calendar c1 = Calendar.getInstance();
		c1.set(2018, 6, 5,18,34,12);
		Calendar c2 = Calendar.getInstance();
		c2.set(2018, 5,20,17,35,12);
		long nd = 24 * 60 * 60;
	    long nh = 60 * 60;
	    long nm = 60;
		long diff = (c1.getTimeInMillis() - c2.getTimeInMillis()) /1000;
		long t = diff / nd;
		long s = diff % (nd) / nh;
		long m = diff % nd % nh / nm;
		System.out.println(t+","+s+","+m);
	}
    
}
