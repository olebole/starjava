package uk.ac.starlink.ttools.build;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import uk.ac.starlink.task.BooleanParameter;
import uk.ac.starlink.task.ChoiceParameter;
import uk.ac.starlink.task.InputStreamParameter;
import uk.ac.starlink.task.Parameter;
import uk.ac.starlink.task.Task;
import uk.ac.starlink.ttools.Stilts;
import uk.ac.starlink.ttools.filter.StepFactory;
import uk.ac.starlink.ttools.task.FilterParameter;
import uk.ac.starlink.ttools.task.InputFormatParameter;
import uk.ac.starlink.ttools.task.LineTableEnvironment;
import uk.ac.starlink.ttools.task.OutputFormatParameter;
import uk.ac.starlink.ttools.task.XmlEncodingParameter;
import uk.ac.starlink.util.LoadException;

public class BashComplete {

    public static String taskCompletition(String taskName)
	throws LoadException {
        Task task = Stilts.getTaskFactory().createObject( taskName );
	Parameter[] params = task.getParameters();
	LineTableEnvironment env = new LineTableEnvironment();
	List outputFormats = env.getTableOutput().getKnownFormats();
	List inputFormats = env.getTableFactory().getKnownFormats();
	Collection charsets = Charset.availableCharsets().keySet();
	String[] filters = StepFactory.getInstance().getFilterFactory().getNickNames();
	String s = "\n_stilts_" + taskName + "() {\n" +
	    "    local args=\"help";
	for ( int i = 0; i < params.length; i++ ) {
	    s += " " + params[i].getName() + "=";
	}
	s += "\"\n" +
	    "    local cur=\"${COMP_WORDS[$COMP_CWORD]/=/}\"\n" +
	    "    local prev=\"${COMP_WORDS[COMP_CWORD-1]}\"\n" +
	    "    if [[ $prev == \"=\" ]] ; then\n" +
	    "        prev=\"${COMP_WORDS[COMP_CWORD-2]}\"\n" +
	    "    fi\n" +
	    "    case $prev in\n";
	ArrayList<String> nousage = new ArrayList<String>();
	ArrayList<String> bool = new ArrayList<String>();
	for ( int i = 0; i < params.length; i++ ) {
	    if (params[i] instanceof BooleanParameter) {
		bool.add(params[i].getName());
		continue;
	    }
	    if (params[i] instanceof ChoiceParameter) {
		s += completionCase(params[i].getName(), ((ChoiceParameter)params[i]).getOptionNames());
		continue;
	    }
	    if (params[i] instanceof OutputFormatParameter) {
		s += completionCase(params[i].getName(), outputFormats);
		continue;
	    }
	    if (params[i] instanceof InputFormatParameter) {
		s += completionCase(params[i].getName(), inputFormats);
		continue;
	    }
	    if (params[i] instanceof InputStreamParameter) {
		s += fileCompletionCase(params[i].getName());
		continue;
	    }
	    if (params[i] instanceof FilterParameter) {
		s += completionCase(params[i].getName(), filters);
		continue;
	    }
	    if (params[i] instanceof XmlEncodingParameter) {
		s += completionCase(params[i].getName(), charsets);
		continue;
	    }
	    String[] u = params[i].getUsage().split("\\|");
	    if (u.length <= 1) {
		nousage.add(params[i].getName());
		continue;
	    }
	    s += completionCase(params[i].getName(), u);
	}
	if (bool.size() > 0) {
	    String[] bools = { "true", "false" };
	    s += completionCase(String.join("|", bool), bools);
	}
	if (nousage.size() > 0) {
	    s += "        " + String.join("|", nousage) + ")\n" +
		"            COMPREPLY=( )\n" +
		"            ;;\n";
	}
	s += "        *)\n" +
	    "            compopt -o nospace\n" +
	    "            COMPREPLY=( $(compgen -W \"$args\" -- $cur) )\n" +
	    "            ;;\n" +
	    "    esac\n" +
	    "}\n" ;
	return s;
    }

    public static String completionCase(String name, Collection pattern) {
	return "        " + name + ")\n" +
	    "            COMPREPLY=( " +
	    "$(compgen -W \"" + String.join(" ", pattern) + "\" -- ${cur}) " +
	    ")\n" +
	    "            ;;\n";
    }

    public static String completionCase(String name, String[] pattern) {
	return "        " + name + ")\n" +
	    "            COMPREPLY=( " +
	    "$(compgen -W \"" + String.join(" ", pattern) + "\" -- ${cur}) " +
	    ")\n" +
	    "            ;;\n";
    }

    public static String fileCompletionCase(String name) {
	return "        " + name + ")\n" +
	    "            COMPREPLY=( " +
	    "$(compgen -f -- ${cur}) " +
	    "$(compgen -d -- ${cur}) " +
	    ")\n" +
	    "            ;;\n";
    }

    public static String stiltsCompletition(String[] taskNames) {
	String[] options = {
	    "-help", "-version", "-verbose", "-allowunused", "-prompt",
	    "-bench", "-debug", "-batch", "-memory", "-disk", "-memgui",
	    "-checkversion", "-stdout", "-stderr"
	};
	return "_stilts() {\n" +
	    "    local tasks=\""+ String.join(" ", taskNames) +"\"\n" +
	    "    local args=\"" + String.join(" ", options) + "\"\n" +
	    "    local cur=\"${COMP_WORDS[$COMP_CWORD]}\"\n" +
	    "    local cmd=\"stilts\"\n" +
	    "    for w in ${COMP_WORDS[@]} ; do\n" +
	    "        for t in $tasks; do\n" +
	    "            if [[ \"$w\" == \"$t\" ]] ; then\n" +
	    "                cmd=stilts_\"$w\"\n" +
	    "            fi\n" +
	    "        done\n" +
	    "    done\n" +
	    "    case $cmd in\n" +
	    "        stilts)\n" +
	    "            if [[ \"$cur\" == -* ]] ; then\n" +
	    "                COMPREPLY=( $(compgen -W \"$args\" -- $cur) )\n" +
	    "            else\n" +
	    "                COMPREPLY=( $(compgen -W \"$tasks\" -- $cur) )\n" +
	    "            fi\n" +
	    "            ;;\n" +
	    "        *)\n" +
	    "            _${cmd}\n" +
	    "            ;;\n" +
	    "      esac\n" +
	    "} &&\n";
    }


    public static void main(String[] args)
	throws IOException, LoadException {
	String[] taskNames = Stilts.getTaskFactory().getNickNames();
	String fname = "stilts";
	PrintWriter out = new PrintWriter(fname);
	out.println("# stilts(1) completion                                     -*- shell-script -*-");
	for ( int i = 0; i < taskNames.length; i++ ) {
	    String taskName = taskNames[ i ];
	    out.print(taskCompletition(taskName));
	}
	out.print(stiltsCompletition(taskNames));
	out.println("complete -F _stilts stilts\n");
	out.println("# ex: ts=4 sw=4 et filetype=sh");
	out.close();
    }
}
