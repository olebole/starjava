package uk.ac.starlink.ttools.build;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

import uk.ac.starlink.task.Parameter;
import uk.ac.starlink.task.Task;
import uk.ac.starlink.ttools.Stilts;
import uk.ac.starlink.util.LoadException;

public class UsageWriterMan {
    public static String manPage(String taskName, Document doc)
	throws IOException, SAXException, LoadException,
	       ParserConfigurationException {
        Task task = Stilts.getTaskFactory().createObject( taskName );
	return ".TH STILTS-" + taskName.toUpperCase() +
	    " 1 \"Mar 2017\" \"\" \"Stilts commands\"\n" +
	    ".SH NAME\n" +
	    "stilts-" + taskName + " \\- " + task.getPurpose() + "\n" +
	    getTaskSynopsis(taskName, task) +
	    getTaskDescription(taskName, doc) +
	    getTaskOptions(task) +
	    ".SH SEE ALSO\n" +
	    "\\fBstilts\\fR(1)\n" +
	    ".PP\n" +
	    "If the package stilts-doc is installed,\n" +
	    "the full documentation \\fBSUN/256\\fR is available in HTML format:\n" +
	    ".br\n\\%file:///usr/share/doc/stilts-doc/sun256/index.html\n" +
	    ".SH VERSION\n" +
	    "STILTS version " + Stilts.getVersion() + "\n" +
	    ".PP\n" +
	    "This is the Debian version of Stilts, which lack the support\n" +
	    "of some file formats and network protocols. For differences see\n" +
	    ".br\n\\%file:///usr/share/doc/stilts/README.Debian\n" +
	    ".SH AUTHOR\n" +
	    "Mark Taylor (Bristol University)\n";
    }

    public static String getTaskSynopsis(String taskName, Task task) {
	String command = "stilts " + taskName;
	String s = ".SH SYNOPSIS\n" +
	    ".ad l\n" +
	    ".HP "+ (command.length() + 1) + "\n" +
	    ".hy 0\n" +
	    "\\fB" + command + "\\fR";
        Parameter[] params = task.getParameters();
	for ( int i = 0; i < params.length; i++ ) {
	    s += " [" + params[i].getName() + "=\\fI" +
		params[i].getUsage() + "\\fR]";
	}
	s += "\n.hy\n.ad\n";
	return s;
    }

    public static String getTaskOptions(Task task)
	throws SAXException, IOException, ParserConfigurationException{
        Parameter[] params = task.getParameters();
        if ( params.length > 0 ) {
            String s = ".SH OPTIONS\n";
            for ( int i = 0; i < params.length; i++ ) {
		s += ".TP\n" +
		    "\\fB" + params[i].getName() + "=\\fI" +
		    params[i].getUsage() + "\\fR\n" +
		    ".RS\n" +
		    formatXML(params[i].getDescription()) + "\n" +
		    ".RE\n";
            }
	    return s;
        } else {
	    return "";
	}
    }

    public static String getTaskDescription(String taskName, Document doc) {
        Node n = doc.getElementById(taskName);
	if (n == null) {
	    return "Description for '" + taskName + "' not found";
	}

	String s = ".SH DESCRIPTION\n";
        for ( Node child = n.getFirstChild(); child != null;
              child = child.getNextSibling() ) {
            if ( child instanceof Element ) {
                Element el = (Element) child;
                String tag = el.getTagName();
                if ( tag.equals( "p" ) ) {
		    s += ".PP\n" + formatXML(child) + "\n";
		}
	    }
	}
	return s;
    }

    public static String manPage(String[] taskNames, Document doc)
	throws LoadException {
	return ".TH STILTS" + " 1 \"Mar 2017\" \"\" \"Stilts commands\"\n" +
	    ".SH NAME\n" +
	    "stilts \\- Starlink Tables Infrastructure Library Tool Set\n" +
	    getStiltsSynopsis() +
	    ".SH DESCRIPTION\n" +
	    "STILTS provides a number of command-line applications which\n" +
	    "can be used for manipulating tabular data. Conceptually it\n" +
	    "sits between, and uses many of the same classes as, the\n" +
	    "packages STIL, which is a set of Java APIs providing\n" +
	    "table-related functionality, and TOPCAT, which is a graphical\n" +
	    "application providing the user with an interactive platform\n" +
	    "for exploring one or more tables.\n" +
	    ".PP\n" +
	    "Detailed help for each task is available with the\n" +
	    "\\fBhelp\\fR option of the task.\n" +
	    getStiltsOptions(doc) +
	    getStiltsTasks(taskNames) +
	    ".SH SEE ALSO\n" +
	    "\\fBstilts-\\fI<task>\\fR(1) for all tasks, \\fBtopcat\\fR(1)\n" +
	    ".PP\n" +
	    "If the package stilts-doc is installed,\n" +
	    "the full documentation \\fBSUN/256\\fR is available in HTML format:\n" +
	    ".br\n\\%file:///usr/share/doc/stilts-doc/sun256/index.html\n" +
	    ".SH VERSION\n" +
	    "STILTS version " + Stilts.getVersion() + "\n" +
	    ".PP\n" +
	    "This is the Debian version of Stilts, which lack the support\n" +
	    "of some file formats and network protocols. For differences see\n" +
	    ".br\n\\%file:///usr/share/doc/stilts/README.Debian\n" +
	    ".SH AUTHOR\n" +
	    "Mark Taylor (Bristol University)\n";
    }

    public static String getStiltsSynopsis() {
	String command = "stilts";
	return ".SH SYNOPSIS\n" +
	    ".ad l\n" +
	    ".HP "+ (command.length() + 1) + "\n" +
	    ".hy 0\n" +
	    "\\fB" + command + "\\fR" +
	    " [-help] [-version] [-verbose] [-allowunused] [-prompt]" +
	    " [-bench] [-debug] [-batch] [-memory] [-disk] [-memgui]" +
	    " [-checkversion <vers>] [-stdout <file>] [-stderr <file>]\n" +
	    ".br\n<task> <task-args>\n" +
	    ".PP\n" +
	    "\\fBstilts\\fR <task> \\fBhelp\\fR[=\\fI<param-name>|*\\fR]\n" +
	    ".hy\n.ad\n";
    }

    public static String getStiltsOptions(Document doc) {
        Node n = doc.getElementById("stilts-flags")
	    .getElementsByTagName("dl")
	    .item(0);
	String s = ".SH OPTIONS\n" +
	    "Some flags are common to all the tasks in the STILTS package,\n" +
	    "and these are specified after the stilts invocation itself and\n" +
	    "before the task name. They generally have the same effect\n" +
	    "regardless of which task is running.\n";
        for ( Node child = n.getFirstChild(); child != null;
              child = child.getNextSibling() ) {
            if ( child instanceof Element ) {
                Element el = (Element) child;
                String tag = el.getTagName();
		if (tag.equals("dt")) {
		    s += ".TP\n\\fB"
			+ el.getTextContent().replace("-", "\\-")
			+ "\\fR\n";
		} else if  (tag.equals("dd")) {
		    s += formatXML(el) + "\n";
		}
	    }
	}
	return s;
    }

    public static String getStiltsTasks(String[] taskNames)
	throws LoadException{
	String s = ".SH STILT TASKS\n" +
	    "The following tasks are currently available:\n";
	for ( int i = 0; i < taskNames.length; i++ ) {
	    s += ".TP\n\\fBstilts-" + taskNames[i] + "\\fR(1)\n";
	    s += Stilts.getTaskFactory().createObject( taskNames[i] ).getPurpose();
	    s += "\n";
	}
	return s;
    }

    public static String formatXML( String xml )
	throws SAXException, IOException, ParserConfigurationException {
	String dxml = "<?xml version='1.0'?><!DOCTYPE doc []><DOC>"
	    + xml + "</DOC>";
	InputStream in = new ByteArrayInputStream( dxml.getBytes() );
	Document dom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse( in );
        return formatXML( dom );
    }

    public static String formatXML( Node doc ) {
        StringBuffer result = new StringBuffer();
        appendChildren( result, doc );
        return result.toString().replaceAll(" +", " ") + "\n";
    }

    private static void appendChildren( StringBuffer result, Node node ) {
	boolean firstParagraph = true;
        for ( Node child = node.getFirstChild(); child != null;
              child = child.getNextSibling() ) {
            if ( child instanceof Element ) {
                Element el = (Element) child;
                String tag = el.getTagName();
                if ( tag.equals( "p" ) ) {
		    if (firstParagraph) {
			firstParagraph = false;
		    } else {
			result.append("\n.PP\n");
		    }
                    appendChildren( result, el );
                }
                else if ( tag.equals( "ul" ) ) {
		    result.append("\n.RS 2\n");
                    appendChildren( result, el );
		    result.append("\n.RE\n");
                }
                else if ( tag.equals( "li" ) ) {
                    result.append( "\n.IP * 2\n" );
                    appendChildren( result, el );
                }
                else if ( tag.equals( "dl" ) ) {
                    appendChildren( result, el );
                }
                else if ( tag.equals( "dt" ) ) {
		    result.append("\n.TP\n");
                    appendChildren( result, el );
                }
                else if ( tag.equals( "dd" ) ) {
                    appendChildren( result, el );
                }
                else if ( tag.equals( "strong" ) ) {
		    result.append("\\fB");
                    appendChildren( result, el );
		    result.append("\\fR");
                }
                else if ( tag.equals( "code" ) ) {
		    result.append("\\fI");
                    appendChildren( result, el );
		    result.append("\\fR");
                }
                else if ( tag.equals( "ref" ) ) {
                    if ( el.getFirstChild() != null ) {
                        appendChildren( result, el );
                    }
                    else {
                        result.append( "SUN/256" );
                    }
                }
                else {
                    appendChildren( result, child );
                }
            }
            else if ( child instanceof Text ) {
                result.append( ((Text) child).getData().replace("\n", " ") );
            }
            else if ( child instanceof DocumentType ) {
            }
            else {
                throw new IllegalArgumentException( "Can't serialize node " +
                                                    child.getClass()
                                                   .getName() );
            }
        }
    }

    public static void main( String[] args )
	throws IOException, LoadException, SAXException,
	       ParserConfigurationException {

        DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        domFactory.setNamespaceAware(true);
        DocumentBuilder docBuilder = domFactory.newDocumentBuilder();
        Document doc = docBuilder.parse("sun256.xml");

	String[] taskNames = Stilts.getTaskFactory().getNickNames();
	String fname = "stilts.man";
	PrintWriter out = new PrintWriter(fname);
	out.print(manPage(taskNames, doc));
	out.close();
	for ( int i = 0; i < taskNames.length; i++ ) {
	    String taskName = taskNames[ i ];
	    fname = "stilts-" + taskName + ".man";
	    System.out.println( "Writing " + fname );
	    out = new PrintWriter( fname );
	    out.print(manPage(taskName, doc));
	    out.close();
	}
    }
}
