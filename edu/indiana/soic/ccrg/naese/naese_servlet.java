package edu.indiana.soic.ccrg.naese;

import java.io.*;
import java.net.*; //URLEncoder,InetAddress
import java.text.*;
import java.util.*;
import java.util.regex.*;
import javax.servlet.*;
import javax.servlet.http.*;

import com.oreilly.servlet.*; //MultipartRequest,Base64Encoder,Base64Decoder
import com.oreilly.servlet.multipart.DefaultFileRenamePolicy;

import com.hp.hpl.jena.ontology.*;
import com.hp.hpl.jena.rdf.model.*; //Model
import com.hp.hpl.jena.util.FileManager;
import com.hp.hpl.jena.util.iterator.*; //ExtendedIterator
import com.hp.hpl.jena.vocabulary.*;
import com.hp.hpl.jena.reasoner.*; //Reasoner, ReasonerRegistry, InfModel
import com.hp.hpl.jena.query.* ; //Query,QueryFactory,QueryExecution,ResultSetFormatter,QueryParseException,ARQ
import com.hp.hpl.jena.sparql.*; //Sparql
import com.hp.hpl.jena.sparql.core.*; //Prologue

import org.apache.jena.atlas.logging.*; //LogCtl

import edu.indiana.soic.ccrg.jena.*;
import edu.unm.health.biocomp.http.*;
import edu.unm.health.biocomp.util.*;

/**	Native American ethnobotony semantic explorer (Naese).

	@author Jeremy J Yang
*/
public class naese_servlet extends HttpServlet
{
  private static String SERVLETNAME=null;
  private static String CONTEXTPATH=null;
  private static String LOGDIR=null;	// configured in web.xml
  private static String APPNAME=null;	// configured in web.xml
  private static String UPLOADDIR=null;	// configured in web.xml
  //private static String DBDIR=null;	// configured in web.xml
  private static String DBNAME=null;	// configured in web.xml
  private static String RDFFILE=null;	// configured in web.xml
  private static int N_MAX=100; // configured in web.xml
  private static String SPARQL_DIR=null;	// configured in web.xml
  private static ServletContext CONTEXT=null;
  //private static ServletConfig CONFIG=null;
  private static ResourceBundle rb=null;
  private static int serverport=0;
  private static String SERVERNAME=null;
  private static String REMOTEHOST=null;
  private static String DATESTR=null;
  private static File logfile=null;
  private static String color1="#EEEEEE";

  private static Model RDFMOD=null; //initialized once per deployment


  // EXAMPLE Sparql headers and queries:
  // Improve by reading these from rq files, from configured SPARQL_DIR.
  // But how to handle headers?

  private static String RQ_HEADERS = null;
  private static ArrayList<String> RQ_EXAMPLES = null;


  //Non-static, owned by object
  private ArrayList<String> outputs=null;
  private ArrayList<String> errors=null;
  private HttpParams params=null;
  private PrintWriter out=null;
  private String RQTXT="";

  /////////////////////////////////////////////////////////////////////////////
  public void doPost(HttpServletRequest request,HttpServletResponse response)
      throws IOException,ServletException
  {
    serverport=request.getServerPort();
    SERVERNAME=request.getServerName();
    if (SERVERNAME.equals("localhost")) SERVERNAME=InetAddress.getLocalHost().getHostAddress();
    REMOTEHOST=request.getHeader("X-Forwarded-For"); // client (original)
    if (REMOTEHOST!=null)
    {
      String[] addrs=Pattern.compile(",").split(REMOTEHOST);
      if (addrs.length>0) REMOTEHOST=addrs[addrs.length-1];
    }
    else
    {
      REMOTEHOST=request.getRemoteAddr(); // client (may be proxy)
    }
    rb=ResourceBundle.getBundle("LocalStrings",request.getLocale());

    MultipartRequest mrequest=null;
    if (request.getMethod().equalsIgnoreCase("POST"))
    {
      try { mrequest=new MultipartRequest(request,UPLOADDIR,10*1024*1024,"ISO-8859-1", new DefaultFileRenamePolicy()); }
      catch (IOException lEx) { this.getServletContext().log("Not a valid MultipartRequest.",lEx); }
    }

    // main logic:
    ArrayList<String> cssincludes = new ArrayList<String>(Arrays.asList("biocomp.css"));
    ArrayList<String> jsincludes = new ArrayList<String>(Arrays.asList("biocomp.js","ddtip.js"));
    boolean ok=initialize(request,mrequest);
    if (!ok)
    {
      response.setContentType("text/html");
      out=response.getWriter();
      out.println(HtmUtils.HeaderHtm(APPNAME,jsincludes,cssincludes,JavaScript(),color1,request));
      out.println(HtmUtils.FooterHtm(errors,true));
      return;
    }
    else if (request.getParameter("help")!=null)	// GET method, help=TRUE
    {
      response.setContentType("text/html");
      out=response.getWriter();
      out.println(HtmUtils.HeaderHtm(APPNAME,jsincludes,cssincludes,JavaScript(),color1,request));
      out.println(HelpHtm());
      out.println(HtmUtils.FooterHtm(errors,true));
    }
    else if (!RQTXT.isEmpty())
    {
      response.setContentType("text/html");
      out=response.getWriter();
      out.println(HtmUtils.HeaderHtm(APPNAME,jsincludes,cssincludes,JavaScript(),color1,request));
      out.println(FormHtm(mrequest,response,params));
      String rqtxt=RQTXT;
      if (params.isChecked("hideheaders")) rqtxt=RQ_HEADERS+"\n"+RQTXT;
      try {
        Query query = QueryFactory.create(rqtxt);
        Prologue prologue = query.getPrologue();
        QueryExecution qe = QueryExecutionFactory.create(query, RDFMOD);
        ResultSet results = qe.execSelect();
        String result_txt = ResultSetFormatter.asText(results,prologue); //abbreviate IRIs
        outputs.add("<pre>"+result_txt.replaceAll("<","&lt;").replaceAll(">","&gt;")+"</pre>");
        qe.close();
        out.println(HtmUtils.OutputHtm(outputs));
      }
      catch (QueryParseException e) {
        errors.add("ERROR:"+e.getMessage());
      }
      out.println(HtmUtils.FooterHtm(errors,true));
    }
    else
    {
      response.setContentType("text/html");
      out=response.getWriter();
      out.println(HtmUtils.HeaderHtm(APPNAME,jsincludes,cssincludes,JavaScript(),color1,request));
      out.println(FormHtm(mrequest,response,params));
      out.println("<SCRIPT>go_init(window.document.mainform)</SCRIPT>");
      out.println(HtmUtils.FooterHtm(errors,true));
    }
  }
  /////////////////////////////////////////////////////////////////////////////
  /**	Called once per request.
  */
  private boolean initialize(HttpServletRequest request,MultipartRequest mrequest)
      throws IOException,ServletException
  {
    SERVLETNAME=this.getServletName();
    this.outputs = new ArrayList<String>();
    this.errors = new ArrayList<String>();
    this.params = new HttpParams();
    this.RQTXT="";

    String logo_htm="<TABLE CELLSPACING=5 CELLPADDING=5><TR><TD>";
    String imghtm=("<IMG BORDER=0 HEIGHT=\"60\" SRC=\"/tomcat"+CONTEXTPATH+"/images/iu_logo.png\">");
    String tiphtm=(APPNAME+" web app from IU SOIC.");
    String href=("http://soic.indiana.edu");
    logo_htm+=(HtmUtils.HtmTipper(imghtm,tiphtm,href,200,"white"));
    logo_htm+="</TD><TD>";
    imghtm=("<IMG BORDER=0 SRC=\"/tomcat"+CONTEXTPATH+"/images/jena-logo-jumbotron.png\">");
    tiphtm=("Apache Jena");
    href=("https://jena.apache.org/");
    logo_htm+=(HtmUtils.HtmTipper(imghtm,tiphtm,href,200,"white"));
    logo_htm+="</TD></TR></TABLE>";
    errors.add("<CENTER>"+logo_htm+"</CENTER>");

    //Create webapp-specific log dir if necessary:
    File dout=new File(LOGDIR);
    if (!dout.exists())
    {
      boolean ok=dout.mkdir();
      System.err.println("LOGDIR creation "+(ok?"succeeded":"failed")+": "+LOGDIR);
      if (!ok)
      {
        errors.add("ERROR: could not create LOGDIR: "+LOGDIR);
        return false;
      }
    }

    String logpath=LOGDIR+"/"+SERVLETNAME+".log";
    logfile=new File(logpath);
    if (!logfile.exists())
    {
      try {
        logfile.createNewFile();
      }
      catch (IOException e)
      {
        errors.add("ERROR: Cannot create log file:"+e.getMessage());
        return false;
      }
      logfile.setWritable(true,true);
      PrintWriter out_log=new PrintWriter(logfile);
      out_log.println("date\tip\tN"); 
      out_log.flush();
      out_log.close();
    }
    if (!logfile.canWrite())
    {
      errors.add("ERROR: Log file not writable.");
      return false;
    }
    BufferedReader buff=new BufferedReader(new FileReader(logfile));
    if (buff==null)
    {
      errors.add("ERROR: Cannot open log file.");
      return false;
    }

    int n_lines=0;
    String line=null;
    String startdate=null;
    while ((line=buff.readLine())!=null)
    {
      ++n_lines;
      String[] fields=Pattern.compile("\\t").split(line);
      if (n_lines==2) startdate=fields[0];
    }
    Calendar calendar=Calendar.getInstance();
    if (n_lines>2)
    {
      calendar.set(Integer.parseInt(startdate.substring(0,4)),
               Integer.parseInt(startdate.substring(4,6))-1,
               Integer.parseInt(startdate.substring(6,8)),
               Integer.parseInt(startdate.substring(8,10)),
               Integer.parseInt(startdate.substring(10,12)),0);

      DateFormat df=DateFormat.getDateInstance(DateFormat.FULL,Locale.US);
      errors.add("since "+df.format(calendar.getTime())+", times used: "+(n_lines-1));
    }

    calendar.setTime(new java.util.Date());
    DATESTR=String.format("%04d%02d%02d%02d%02d",
      calendar.get(Calendar.YEAR),
      calendar.get(Calendar.MONTH)+1,
      calendar.get(Calendar.DAY_OF_MONTH),
      calendar.get(Calendar.HOUR_OF_DAY),
      calendar.get(Calendar.MINUTE));

    for (Enumeration e=request.getParameterNames(); e.hasMoreElements(); ) //GET
    {
      String key=(String)e.nextElement();
      if (request.getParameter(key)!=null) params.setVal(key,request.getParameter(key));
    }

    //Summarize RDFMOD:
    errors.add("RDF model name: "+DBNAME);
    errors.add("RDF model file: "+RDFFILE);
    errors.add("RDF model size: "+RDFMOD.size());
    StmtIterator stmt_iter = RDFMOD.listStatements();
    int i_stmt=0;
    while (stmt_iter.hasNext())
    {
      ++i_stmt;
      Statement stmt = stmt_iter.nextStatement();
      Resource subj = stmt.getSubject();
      Property prop = stmt.getPredicate();
      RDFNode obj = stmt.getObject();
      //if (params.isChecked("verbose")) errors.add(subj.toString()+"\t"+prop.toString()+"\t"+obj.toString()+" ("+stmt.getLanguage()+")");
    }
    errors.add("RDF statement count: "+i_stmt);

    errors.add("Jena version: "+jena.version.VERSION);
    errors.add("Jena-ARQ version: "+ARQ.VERSION);

    if (params.isChecked("verbose"))
      errors.add("app server: "+CONTEXT.getServerInfo()+" [API:"+CONTEXT.getMajorVersion()+"."+CONTEXT.getMinorVersion()+"]");


    if (mrequest==null) return true; //i.e. GET method

    for (Enumeration e=mrequest.getParameterNames(); e.hasMoreElements(); ) //POST
    {
      String key=(String)e.nextElement();
      if (mrequest.getParameter(key)!=null) params.setVal(key,mrequest.getParameter(key));
    }

    String fname="infile";
    File fin=mrequest.getFile(fname);
    if (fin!=null)
    {
      {
        BufferedReader br=new BufferedReader(new FileReader(fin));
        RQTXT="";
        while ((line=br.readLine())!=null) RQTXT+=(line+"\n");
      }
      params.setVal("rqtxt",RQTXT);
    }
    else
    {
      RQTXT=params.getVal("rqtxt");
    }

    return true;
  }
  /////////////////////////////////////////////////////////////////////////////
  private static String FormHtm(MultipartRequest mrequest,HttpServletResponse response,
	HttpParams params)
      throws IOException
  {
    String exquery_menu="<SELECT NAME=\"exquery\" onChange=\"fix_exquery(this.form)\">\n";
    for (int i=1;i<=RQ_EXAMPLES.size();++i)
      exquery_menu+=("<OPTION VALUE=\""+i+"\">Example "+i+"\n");
    exquery_menu+=("</SELECT>");
    exquery_menu=exquery_menu.replace("\""+params.getVal("exquery")+"\">", "\""+params.getVal("exquery")+"\" SELECTED>");

    String htm=
    ("<FORM NAME=\"mainform\" METHOD=POST ACTION=\""+response.encodeURL(SERVLETNAME)+"\"")
    +(" ENCTYPE=\"multipart/form-data\">\n")
    +("<TABLE BGCOLOR=\"#CCCCFF\" WIDTH=\"100%\" CELLSPACING=\"0\" CELLPADDING=\"0\">\n")
    +("<TR BGCOLOR=\"#CCCCFF\"><TD COLSPAN=\"3\" ALIGN=\"CENTER\"><IMG SRC=\"images/banner_plants.jpg\"></TD></TR>\n")
    +("<TR BGCOLOR=\"#CCCCFF\"><TD COLSPAN=\"3\" ALIGN=\"CENTER\"><P><DIV STYLE=\"font-size:24px; font-weight:bold; margin-top:5; margin-bottom:5; color:#A05000\">Semantic Exploration of Native American Ethnobotany</DIV></P></TD></TR>\n")
    +("<TR BGCOLOR=\"#CCCCFF\">\n")
    +("<TD WIDTH=\"15%\" ALIGN=\"CENTER\" VALIGN=\"TOP\"><H3>"+APPNAME+"</H3>\n")
    +("<P>\n")
    +("<BUTTON TYPE=BUTTON onClick=\"void window.open('"+response.encodeURL(SERVLETNAME)+"?help=TRUE&verbose=TRUE','helpwin','width=600,height=400,scrollbars=1,resizable=1')\"><B>Help</B></BUTTON>\n")
    +("</TD><TD WIDTH=\"70%\" ALIGN=\"CENTER\" VALIGN=\"MIDDLE\">\n")
    +("<TEXTAREA STYLE=\"padding:5px; font-size:12px;\" NAME=\"rqtxt\" WRAP=\"OFF\" ROWS=\"16\" COLS=\"80\">\n")+params.getVal("rqtxt")+("</TEXTAREA>\n")
    +("</TD>\n")
    +("<TD WIDTH=\"15%\" ALIGN=\"CENTER\" VALIGN=\"TOP\">\n")
    +("<B>Input Sparql:</B><BR>\n")
    +("<P>\n")
    +("select example:<BR>\n")
    +("&nbsp;&nbsp;"+exquery_menu+"<BR>\n")
    +("&nbsp;&nbsp;<INPUT TYPE=\"CHECKBOX\" NAME=\"hideheaders\" onChange=\"fix_exquery(this.form)\" VALUE=\"CHECKED\""+params.getVal("hideheaders")+">hide headers<BR>\n")
    +("or upload:<BR>\n<INPUT TYPE=\"FILE\" NAME=\"infile\" STYLE=\"width:80px\"><BR>\n(...or paste)\n")
    +("<P>\n")
    +("<BUTTON TYPE=BUTTON onClick=\"window.location.replace('"+response.encodeURL(SERVLETNAME)+"')\"><B>Reset</B></BUTTON>\n")
    +("<P>\n")
    +("<HR WIDTH=\"80%\">\n")
    +("<P>\n")
    +("<BUTTON TYPE=\"button\" onClick=\"this.form.submit()\"><DIV STYLE=\"font-size:14px; font-weight:bold\">Go "+APPNAME+"</DIV></BUTTON>\n")
    +("</TD></TR></TABLE>\n")
    +("</FORM>\n");
    return htm;
  }
  /////////////////////////////////////////////////////////////////////////////
  private static String JavaScript()
  {
    String js=(
"var RQ_EXAMPLES = [];\n"+
"var RQ_HEADERS = '';\n");

    for (String line: Pattern.compile("[\\n\\r]").split(RQ_HEADERS))
      js+=("RQ_HEADERS+='"+line+"\\n';\n");

    for (String rq_example: RQ_EXAMPLES)
    {
      js+=("var rq='';\n");
      for (String line: Pattern.compile("[\\n\\r]").split(rq_example))
        js+=("rq+='"+line.replaceAll("'","\\\\'")+"\\n';\n");
      js+=("RQ_EXAMPLES.push(rq);\n");
    }

    js+=(
"function go_init(form)\n"+
"{\n"+
"  form.hideheaders.checked=false;\n"+
"  loadExample(form,1,form.hideheaders.checked);\n"+
"}\n"+
"function loadExample(form,i,hideh)\n"+
"{\n"+
"  var rq='';\n"+
"  if (!hideh)\n"+
"  {\n"+
"   rq=RQ_HEADERS+'\\n';\n"+
"  }\n"+
"  rq+=RQ_EXAMPLES[i-1];\n"+
"  form.rqtxt.value=rq;\n"+
"}\n"+
"function fix_exquery(form)\n"+
"{\n"+
"  loadExample(form,form.exquery.value,form.hideheaders.checked);\n"+
"}\n"+
"function checkform(form)\n"+
"{\n"+
"  if (!form.rqtxt.value && !form.infile.value) {\n"+
"    alert('ERROR: No query specified');\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n");
    return js;
  }
  /////////////////////////////////////////////////////////////////////////////
  private static String HelpHtm()
  {
    return (
    "<H1>"+APPNAME+" help</H1>\n"+
    "Naese means Semantic Exploration of Native American Ethnobotany (NAE).\n"+
    "<P>\n"+
    "This database is an RDF triple store integrated from multiple sources, starting with the NAE Database of U. Michigan.\n"+
    "\n"+
    "This web app provides Sparql query access.  Input modes:\n"+
    "<UL>\n"+
    "<LI>Input Sparql manually.\n"+
    "<LI>Upload Sparql file.\n"+
    "<LI>Select Sparql example.\n"+
    "</UL>\n"+
    "The <b>hide headers</b> checkbox simply hides the PREFIX statements, for examples only, but does not affect the query.\n"+
    "<P>\n"+
    "References:\n"+
    "<OL>\n"+
    "<LI><A href=\"http://herb.umd.umich.edu/\">Native American Ethnobotany Database</A>, from University of Michigan - Dearborn\n"+
    "<LI><A href=\"http://jena.apache.org/\">Jena</A>\n"+
    "<LI><A href=\"http://plants.usda.gov/\">USDA Plants Database</A>\n"+
    "<LI><A href=\"http://www.ncbi.nlm.nih.gov/taxonomy/\">NCBI Taxonomy Database</A>\n"+
    "</OL>\n"+
    "<P>\n"+
    "data modeling: Stefan Furrer<br/>\n"+
    "webapp: Jeremy Yang\n"
    );
  }
  /////////////////////////////////////////////////////////////////////////////
  /**	Called once per servlet instantiation; read servlet parameters (from web.xml).
  */
  public void init(ServletConfig conf) throws ServletException
  {
    super.init(conf);
    CONTEXT=getServletContext();	// inherited method
    CONTEXTPATH=CONTEXT.getContextPath();
    //CONFIG=conf;
    APPNAME=conf.getInitParameter("APPNAME");
    if (APPNAME==null) APPNAME=this.getServletName();
    UPLOADDIR=conf.getInitParameter("UPLOADDIR");
    if (UPLOADDIR==null)
      throw new ServletException("Please supply UPLOADDIR parameter.");
    DBNAME=conf.getInitParameter("DBNAME");
    if (DBNAME==null) DBNAME="NAESE";
    RDFFILE=conf.getInitParameter("RDFFILE");
    if (RDFFILE==null)
      throw new ServletException("Please supply RDFFILE parameter.");

    //Read RDFFILE into RDF data model RDFMOD, only once per initialization.
    RDFMOD = ModelFactory.createDefaultModel();
    InputStream instr = FileManager.get().open(RDFFILE);
    if (instr==null) { throw new ServletException("RDFFILE not found: "+RDFFILE); }
    String fext = RDFFILE.substring(1+RDFFILE.lastIndexOf('.'));
    String dlang = (fext.equalsIgnoreCase("ttl")?"TTL":(fext.equalsIgnoreCase("n3")?"N3":"RDF/XML"));
    try { RDFMOD.read(instr,"",dlang); } //arg2=base_uri, arg3=lang
    catch (Exception e) { CONTEXT.log("ERROR: "+e.getMessage()); }

    SPARQL_DIR=conf.getInitParameter("SPARQL_DIR");
    if (SPARQL_DIR==null) SPARQL_DIR="/usr/local/tomcat/webapps/jena/sparql";

    LOGDIR=conf.getInitParameter("LOGDIR")+CONTEXTPATH;
    if (LOGDIR==null) LOGDIR="/usr/local/tomcat/logs"+CONTEXTPATH;
    try { N_MAX=Integer.parseInt(conf.getInitParameter("N_MAX")); }
    catch (Exception e) { N_MAX=100; }

    RQ_HEADERS =
"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"+
"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"+
"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"+
"PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"+
"PREFIX dc: <http://purl.org/dc/elements/1.1/>\n"+
"PREFIX foaf: <http://xmlns.com/foaf/0.1/>\n"+
"PREFIX tax: <http://bio2rdf.org/taxonomy:>\n"+
"PREFIX mesh: <http://bio2rdf.org/mesh:>\n"+
"PREFIX usda: <http://plants.usda.gov/core/profile?symbol=>\n"+
"PREFIX local: <http://naese.local/>\n";

    RQ_EXAMPLES = new ArrayList<String>(Arrays.asList(
"SELECT ?id ?species ?tribe ?title ?sameAs ?seeAlso ?subClassOf ?subPropertyOf\n"+
"WHERE\n"+
"{\n"+
"  FILTER (regex(?species, 'Hibiscus', 'i')) .\n"+
"  ?id rdf:subject ?species .\n"+
"  ?id rdfs:seeAlso ?seeAlso .\n"+
"  ?id owl:sameAs ?sameAs .\n"+
"  ?id foaf:member ?usage .\n"+
"  ?usage dc:title ?title .\n"+
"  ?usage foaf:name ?tribe .\n"+
"  ?usage rdfs:subClassOf ?subClassOf .\n"+
"  ?usage rdfs:subPropertyOf ?subPropertyOf .\n"+
"}\n"+
"ORDER BY ?species\n",

"SELECT ?common_name ?tribe ?medical_class ?title ?plants_uri ?ncbi_tax ?mesh_id\n"+
"WHERE {\n"+
"  ?id dc:subject 'Hibiscus tiliaceus L.' .\n"+
"  ?id rdf:subject ?common_name .\n"+
"  ?id rdfs:seeAlso ?plants_uri .\n"+
"  ?id owl:sameAs ?ncbi_tax .\n"+
"  ?id foaf:member ?usage .\n"+
"  ?usage dc:title ?title .\n"+
"  ?usage foaf:name ?tribe .\n"+
"  ?usage rdfs:subClassOf ?medical_class .\n"+
"  ?usage rdfs:subPropertyOf ?mesh_id .\n"+
"}\n",

"SELECT DISTINCT ?id ?tribe ?species ?subClassOf ?title\n"+
"WHERE {\n"+
"  FILTER (regex(?tribe, 'Cherokee', 'i')) .\n"+
"  ?usage foaf:name ?tribe .\n"+
"  ?usage dc:title ?title .\n"+
"  ?id foaf:member ?usage .\n"+
"  ?id dc:subject ?species .\n"+
"  ?id owl:sameAs ?sameAs .\n"+
"  ?usage rdfs:subClassOf ?subClassOf .\n"+
"}\n"+
"ORDER BY ?subClassOf ?species\n",

"SELECT DISTINCT ?id ?species ?tribe ?subClassOf ?title\n"+
"WHERE {\n"+
"  FILTER (regex(?subClassOf, 'Toothache', 'i')) .\n"+
"  ?usage rdfs:subClassOf ?subClassOf .\n"+
"  ?usage foaf:name ?tribe .\n"+
"  ?usage dc:title ?title .\n"+
"  ?id foaf:member ?usage .\n"+
"  ?id dc:subject ?species .\n"+
"}\n"+
"ORDER BY ?species ?tribe\n",

"SELECT ?species (COUNT(?tribe) AS ?count)\n"+
"WHERE {\n"+
"  FILTER (regex(?subClassOf,  'Dermatological Aid', 'i')) .\n"+
"  ?usage rdfs:subClassOf ?subClassOf .\n"+
"  ?id rdf:subject ?species .\n"+
"  ?id rdfs:seeAlso ?seeAlso .\n"+
"  ?id owl:sameAs ?sameAs .\n"+
"  ?id foaf:member ?usage .\n"+
"  ?usage dc:title ?title .\n"+
"  ?usage foaf:name ?tribe .\n"+
"  ?usage rdfs:subPropertyOf ?subPropertyOf .\n"+
"}\n"+
"GROUP BY ?species\n"+
"ORDER BY DESC(?count)\n"+
"LIMIT 10\n"

));
  }
  /////////////////////////////////////////////////////////////////////////////
  public void doGet(HttpServletRequest request,HttpServletResponse response)
      throws IOException, ServletException
  {
    doPost(request,response);
  }
}
