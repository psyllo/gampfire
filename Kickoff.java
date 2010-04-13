import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;
import java.io.File;

class Kickoff {

    // Arg1 = Account name (i.e. THIS_PART.campfirenow.com)
    // Arg2 = API key
    Kickoff(String[] args) throws Exception {
        ClassLoader parent = getClass().getClassLoader();
        GroovyClassLoader loader = new GroovyClassLoader(parent);
        Class gmainClass = loader.parseClass(new File("UiLauncher.groovy"));
        //Class gmainClass = loader.loadClass("UiLauncher", true, true);
        GroovyObject gmainObj = (GroovyObject) gmainClass.newInstance();
        gmainObj.invokeMethod("launch", args);
    }

    public static void main(String[] args) throws Throwable {
        new Kickoff(args);
    }
}
