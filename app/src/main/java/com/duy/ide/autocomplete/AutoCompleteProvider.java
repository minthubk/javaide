package com.duy.ide.autocomplete;

import android.content.Context;
import android.util.Log;
import android.widget.EditText;

import com.duy.ide.autocomplete.autocomplete.AutoCompletePackage;
import com.duy.ide.autocomplete.autocomplete.PackageImporter;
import com.duy.ide.autocomplete.autocomplete.PatternFactory;
import com.duy.ide.autocomplete.dex.JavaClassReader;
import com.duy.ide.autocomplete.dex.JavaDexClassLoader;
import com.duy.ide.autocomplete.model.ClassDescription;
import com.duy.ide.autocomplete.model.ConstructorDescription;
import com.duy.ide.autocomplete.model.Description;
import com.duy.ide.autocomplete.model.Member;
import com.duy.ide.autocomplete.util.EditorUtil;
import com.duy.ide.file.FileManager;
import com.duy.project.file.java.JavaProjectFolder;

import java.io.File;
import java.util.ArrayList;

import static com.duy.ide.autocomplete.dex.JavaClassManager.determineClassName;


/**
 * Created by Duy on 20-Jul-17.
 */

public class AutoCompleteProvider {
    private static final String TAG = "AutoCompleteProvider";
    private JavaDexClassLoader mClassLoader;
    private Class preReturnType;
    private PackageImporter packageImporter;
    private AutoCompletePackage completePackage;

    public AutoCompleteProvider(Context context) {
        File outDir = context.getDir("dex", Context.MODE_PRIVATE);
        mClassLoader = new JavaDexClassLoader(FileManager.getClasspathFile(context), outDir);
    }

    public void load(JavaProjectFolder projectFile) {
        mClassLoader.loadAllClasses(true, projectFile);
    }

    public boolean isLoaded() {
        return mClassLoader.getClassReader().isLoaded();
    }


    public ArrayList<Description> getSuggestions(EditText editor, int position) {
        // text: 'package.Class.me', prefix: 'package.Class', suffix: 'me'
        // text: 'package.Cla', prefix: 'package', suffix: 'Cla'
        // text: 'Cla', prefix: '', suffix: 'Cla'
        // line: 'new Cla', text: 'Cla', prevWord: 'new'
        String preWord = EditorUtil.getPreWord(editor, position);
        String current = EditorUtil.getWord(editor, position).replace("@", "");
        String prefix = "";
        String suffix = "";
        if (current.contains(".")) {
            prefix = current.substring(0, current.lastIndexOf("."));
            suffix = current.substring(current.lastIndexOf(".") + 1);
        } else {
            suffix = current;
        }
        Log.d(TAG, "getSuggestions suffix = " + suffix + " prefix = " + prefix + " current = " + current
                + " preWord = " + preWord);
        boolean couldBeClass = suffix.matches(PatternFactory.IDENTIFIER.toString());

        ArrayList<Description> result = null;

        if (couldBeClass) {
            Log.d(TAG, "getSuggestions couldBeClass = " + true);
            ArrayList<ClassDescription> classes = this.mClassLoader.findClass(current);

            //Object o = new Object(); //handle new keyword
            if (preWord != null && preWord.equals("new")) {
                result = new ArrayList<>();
                for (ClassDescription description : classes) {
                    ArrayList<ConstructorDescription> constructors = description.getConstructors();
                    for (ConstructorDescription constructor : constructors) {
                        result.add(constructor);
                    }
                }
                return result;
            } else {
                result = new ArrayList<>();
                for (ClassDescription aClass : classes) {
                    if (!aClass.getSimpleName().equals(current)) {
                        result.add(aClass);
                    }
                }
            }
        }


        if (result == null || result.size() == 0) {
            ArrayList<String> classes = determineClassName(editor, position, current, prefix, suffix, preReturnType);
            if (classes != null) {
                for (String className : classes) {
                    JavaClassReader classReader = mClassLoader.getClassReader();
                    ClassDescription classDescription = classReader.readClassByName(className);
                    Log.d(TAG, "getSuggestions classDescription = " + classDescription);
                    if (classDescription != null) {
                        result = new ArrayList<>();
                        result.addAll(classDescription.getMember(suffix));
                    }
                }
            }/* else {
                if (prefix.isEmpty() && !suffix.isEmpty()) {
                    if (JavaUtil.isValidClassName(suffix)) { //could be class
                        ArrayList<ClassDescription> possibleClass = mClassLoader.findClass(suffix);
                    }
                }
            }*/
        }
        return result;
    }

    public String getFormattedReturnType(Member member) {
        // TODO: 20-Jul-17
        return null;
    }

    private String createSnippet(Description desc, String line, String prefix, boolean addMemberClass) {
//        boolean useFullClassName = desc.getType().equals("class")
//                ? line.matches("^(import)") : prefix.contains(".");
//        String text = useFullClassName ? desc.getClassName() : desc.getSimpleName();
//        if (desc.getMember() != null) {
//            text = (addMemberClass ? "${1:" + text + "}." : "")
//                    + createMemberSnippet(desc.getMember(), desc.getType());
//        }
//        return text;
        return "";
    }

    private String createMemberSnippet(Description member) {
        return null;
        // TODO: 20-Jul-17
    }

    public void onInsertSuggestion(EditText editText, Description suggestion) {
        if (suggestion instanceof ClassDescription) {
//            if (!suggestion.getSnippet().contains(".")) {
            PackageImporter.importClass(editText, ((ClassDescription) suggestion).getClassName());
//            }/
        } else if (suggestion instanceof ConstructorDescription) {
            PackageImporter.importClass(editText, suggestion.getName());
        } else if (suggestion instanceof Member) {
            this.preReturnType = suggestion.getType();
        }
//        mClassLoader.touchClass(suggestion.getDescription());
    }

    public void dispose() {
        mClassLoader.getClassReader().dispose();
    }
}
