package com.hannesdorfmann.annotatedadapter.processor.generator;

import com.hannesdorfmann.annotatedadapter.AbsListViewAdapterDelegator;
import com.hannesdorfmann.annotatedadapter.processor.AdapterInfo;
import com.hannesdorfmann.annotatedadapter.processor.FieldInfo;
import com.hannesdorfmann.annotatedadapter.processor.ViewTypeInfo;
import com.hannesdorfmann.annotatedadapter.processor.ViewTypeSearcher;
import com.hannesdorfmann.annotatedadapter.processor.util.ProcessorMessage;
import com.hannesdorfmann.annotatedadapter.processor.util.TypeHelper;
import dagger.ObjectGraph;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;
import repacked.com.squareup.javawriter.JavaWriter;

/**
 * @author Hannes Dorfmann
 */
public class AbsListViewGenerator implements CodeGenerator {

  private AdapterInfo info;
  @Inject Filer filer;
  @Inject TypeHelper typeHelper;
  @Inject ProcessorMessage logger;

  private Map<String, AdapterInfo> adaptersMap;

  public AbsListViewGenerator(ObjectGraph graph, AdapterInfo info,
      Map<String, AdapterInfo> adaptersMap) {
    this.info = info;
    this.adaptersMap = adaptersMap;
    graph.inject(this);
  }

  private void generateAdapterHelper() throws IOException {

    String packageName = typeHelper.getPackageName(info.getAdapterClass());
    String delegatorClassName = info.getAdapterDelegatorClassName();
    String delegatorBinderName = packageName + "." + delegatorClassName;

    //
    // Write code
    //

    JavaFileObject jfo = filer.createSourceFile(delegatorBinderName, info.getAdapterClass());
    Writer writer = jfo.openWriter();
    JavaWriter jw = new JavaWriter(writer);

    jw.emitPackage(packageName);
    jw.emitImports("android.view.View", "android.view.ViewGroup");
    jw.emitEmptyLine();
    jw.emitJavadoc("Generated class by AnnotatedAdapter . Do not modify this code!");

    String superAnnotatedAapterDelegatorClassName = null;
    AdapterInfo superAnnotatedAdaper = info.getAnnotatedAdapterSuperClass(adaptersMap);
    if (superAnnotatedAdaper != null) {
      superAnnotatedAapterDelegatorClassName =
          superAnnotatedAdaper.getQualifiedAdapterDelegatorClassName();
    }

    jw.beginType(delegatorClassName, "class", EnumSet.of(Modifier.PUBLIC),
        superAnnotatedAapterDelegatorClassName,
        AbsListViewAdapterDelegator.class.getCanonicalName());
    jw.emitEmptyLine();
    jw.emitEmptyLine();

    // Check binder interface implemented
    jw.beginMethod("void", "checkBinderInterfaceImplemented", EnumSet.of(Modifier.PUBLIC),
        ViewTypeSearcher.LISTVIEW_ADAPTER, "adapter");
    jw.emitEmptyLine();
    jw.beginControlFlow("if (!(adapter instanceof %s)) ", info.getBinderClassName());
    jw.emitStatement(
        "throw new java.lang.RuntimeException(\"The adapter class %s must implement the binder interface %s \")",
        info.getAdapterClassName(), info.getBinderClassName());
    jw.endControlFlow();
    jw.endMethod();

    // ViewTypeCount
    jw.beginMethod("int", "getViewTypeCount", EnumSet.of(Modifier.PUBLIC));
    if (info.hasAnnotatedAdapterSuperClass(adaptersMap)) {
      jw.emitStatement("return super.getViewTypeCount() + %d", info.getViewTypes().size());
    } else {
      jw.emitStatement("return %d", info.getViewTypes().size());
    }
    jw.endMethod();

    jw.emitEmptyLine();
    jw.emitEmptyLine();

    // onCreateViewHolder
    jw.beginMethod("View", "onCreateViewHolder", EnumSet.of(Modifier.PUBLIC),
        ViewTypeSearcher.LISTVIEW_ADAPTER, "adapter", "ViewGroup", "parent", "int", "viewType");

    jw.emitEmptyLine();
    jw.emitStatement("%s ad = (%s) adapter", info.getAdapterClass(), info.getAdapterClass());
    jw.emitEmptyLine();

    int ifs = 0;

    for (ViewTypeInfo vt : info.getViewTypes()) {
      jw.beginControlFlow((ifs > 0 ? "else " : "") + "if (viewType == ad.%s)", vt.getFieldName());
      jw.emitStatement("View view = ad.getInflater().inflate(%d, parent, false)",
          vt.getLayoutRes());
      jw.emitStatement("%s.%s vh = new %s.%s(view)", info.getViewHoldersClassName(),
          vt.getViewHolderClassName(), info.getViewHoldersClassName(), vt.getViewHolderClassName());
      jw.emitStatement("view.setTag(vh)");
      if (vt.hasViewHolderInitMethod()) {
        jw.emitStatement("%s binder = (%s) adapter", info.getBinderClassName(),
            info.getBinderClassName());
        jw.emitStatement("binder.%s(vh, view, parent)", vt.getInitMethodName());
      }
      jw.emitStatement("return view");
      jw.endControlFlow();
      jw.emitEmptyLine();
      ifs++;
    }

    if (info.hasAnnotatedAdapterSuperClass(adaptersMap)) {
      jw.emitStatement("return super.onCreateViewHolder(adapter, parent, viewType)");
    } else {
      jw.emitStatement(
          "throw new java.lang.IllegalArgumentException(\"Unknown view type \"+viewType)");
    }
    jw.endMethod();

    jw.emitEmptyLine();
    jw.emitEmptyLine();

    // onBindViewHolder
    jw.beginMethod("void", "onBindViewHolder", EnumSet.of(Modifier.PUBLIC),
        ViewTypeSearcher.LISTVIEW_ADAPTER, "adapter", "View", "view", "int", "position", "int",
        "viewType");

    jw.emitEmptyLine();
    jw.emitStatement("%s castedAdapter = (%s) adapter", info.getAdapterClassName(),
        info.getAdapterClassName());
    jw.emitStatement("%s binder = (%s) adapter", info.getBinderClassName(),
        info.getBinderClassName());
    jw.emitStatement("Object vh = view.getTag()");

    ifs = 0;
    for (ViewTypeInfo vt : info.getViewTypes()) {
      jw.beginControlFlow((ifs > 0 ? "else " : "") + "if (castedAdapter.%s == viewType)",
          vt.getFieldName());

      StringBuilder builder = new StringBuilder("binder.");
      builder.append(vt.getBinderMethodName());
      builder.append("( (");
      builder.append(info.getViewHoldersClassName());
      builder.append(".");
      builder.append(vt.getViewHolderClassName());
      builder.append(") vh, position");
     /*
      if (vt.hasModelClass()) {
        builder.append(", (");
        builder.append(vt.getQualifiedModelClass());
        builder.append(") getItem(position)");
      }
      */
      builder.append(")");

      jw.emitStatement(builder.toString());
      jw.emitStatement("return");
      jw.endControlFlow();
      ifs++;
    }

    if (info.hasAnnotatedAdapterSuperClass(adaptersMap)) {
      jw.emitStatement("super.onBindViewHolder(adapter, view, position, viewType)");
    } else {
      jw.emitStatement(
          "throw new java.lang.IllegalArgumentException(" + "\"Binder method not found\")");
    }
    jw.endMethod();

    jw.endType();
    jw.close();
  }

  private void generateBinderInterface() throws IOException {

    String packageName = typeHelper.getPackageName(info.getAdapterClass());
    String binderClassName = info.getBinderClassName();
    String qualifiedBinderName = packageName + "." + binderClassName;

    //
    // Write code
    //

    JavaFileObject jfo = filer.createSourceFile(qualifiedBinderName, info.getAdapterClass());
    Writer writer = jfo.openWriter();
    JavaWriter jw = new JavaWriter(writer);

    jw.emitPackage(packageName);

    jw.emitStaticImports(info.getQualifiedViewHoldersClassName() + ".*");

    jw.emitJavadoc("Generated class by AnnotatedAdapter . Do not modify this code!");

    AdapterInfo superAnnotatedAdapter = info.getAnnotatedAdapterSuperClass(adaptersMap);
    String qualifiedSuperAnnotateAdapterName = null;
    if (superAnnotatedAdapter != null) {
      qualifiedSuperAnnotateAdapterName = superAnnotatedAdapter.getQualifiedBinderClassName();
    }
    jw.beginType(binderClassName, "interface", EnumSet.of(Modifier.PUBLIC),
        qualifiedSuperAnnotateAdapterName);
    jw.emitEmptyLine();

    for (ViewTypeInfo vt : info.getViewTypes()) {

      if (vt.hasViewHolderInitMethod()) {
        // Generate the init method
        jw.emitEmptyLine();
        jw.beginMethod("void", vt.getInitMethodName(), EnumSet.of(Modifier.PUBLIC),
            vt.getViewHolderClassName(), "vh", "android.view.View", "view",
            "android.view.ViewGroup", "parent");
        jw.endMethod();
      }

      jw.emitEmptyLine();
      List<String> params = new ArrayList(6);

      params.add(vt.getViewHolderClassName());
      params.add("vh");

      params.add("int");
      params.add("position");
/*
      if (vt.hasModelClass()) {
        params.add(vt.getQualifiedModelClass());
        params.add("model");
      }
*/
      jw.beginMethod("void", vt.getBinderMethodName(), EnumSet.of(Modifier.PUBLIC), params, null);

      jw.endMethod();
      jw.emitEmptyLine();
    }

    jw.endType();
    jw.close();
  }

  private void generateViewHolders() throws IOException {

    String packageName = typeHelper.getPackageName(info.getAdapterClass());
    String holdersClassName = info.getViewHoldersClassName();
    String qualifiedHoldersName = packageName + "." + holdersClassName;

    //
    // Write code
    //

    JavaFileObject jfo = filer.createSourceFile(qualifiedHoldersName, info.getAdapterClass());
    Writer writer = jfo.openWriter();
    JavaWriter jw = new JavaWriter(writer);

    // Class things
    jw.emitPackage(packageName);
    jw.emitJavadoc("Generated class by AnnotatedAdapter . Do not modify this code!");
    jw.beginType(holdersClassName, "class", EnumSet.of(Modifier.PUBLIC));
    jw.emitEmptyLine();

    jw.beginConstructor(EnumSet.of(Modifier.PRIVATE));
    jw.endConstructor();

    jw.emitEmptyLine();
    jw.emitEmptyLine();

    for (ViewTypeInfo v : info.getViewTypes()) {
      jw.beginType(v.getViewHolderClassName(), "class",
          EnumSet.of(Modifier.PUBLIC, Modifier.STATIC));
      jw.emitEmptyLine();

      // Insert fields
      for (FieldInfo f : v.getFields()) {
        jw.emitField(f.getQualifiedClassName(), f.getFieldName(), EnumSet.of(Modifier.PUBLIC));
      }

      if (!v.getFields().isEmpty()) {
        jw.emitEmptyLine();
      }

      jw.beginConstructor(EnumSet.of(Modifier.PUBLIC), "android.view.View", "view");
      jw.emitEmptyLine();
      for (FieldInfo f : v.getFields()) {
        jw.emitStatement("%s = (%s) view.findViewById(%d)", f.getFieldName(),
            f.getQualifiedClassName(), f.getId());
      }
      jw.endConstructor();
      jw.endType();

      jw.emitEmptyLine();
      jw.emitEmptyLine();
    }

    jw.endType(); // End of holders class
    jw.close();
  }

  private boolean checkViewTypeIntegerValues() {

    HashMap<Integer, Pair<AdapterInfo, ViewTypeInfo>> valuesMap =
        new HashMap<Integer, Pair<AdapterInfo, ViewTypeInfo>>();

    AdapterInfo adapterInfo = info;

    while (adapterInfo != null) {
      for (ViewTypeInfo vt : adapterInfo.getViewTypes()) {

        if (!vt.isCheckIntegerValue()) { // Skip if not checked
          continue;
        }

        Pair<AdapterInfo, ViewTypeInfo> found = valuesMap.get(vt.getIntegerValue());
        if (found != null) {

          Element causeElement = found.second.getElement();
          String firstVT = found.first.getAdapterClassName() + "." + found.second.getFieldName();
          String secondVT = adapterInfo.getAdapterClassName() + "." + vt.getFieldName();

          if (adapterInfo == info) {
            // Swap to get user friendly error message if the viewtypes with same id are in the same java file
            String tmp = firstVT;
            firstVT = secondVT;
            secondVT = tmp;
            causeElement = vt.getElement();
          }

          logger.error(causeElement, "The @ViewType %s has the same value = %d as @ViewType %s . "
              + "You can disable this check with @ViewType( checkValue = false) if and only"
              + " if you have very good reasons", firstVT, vt.getIntegerValue(), secondVT);
          return false;
        }

        valuesMap.put(vt.getIntegerValue(), Pair.create(adapterInfo, vt));
      }

      adapterInfo = adapterInfo.getAnnotatedAdapterSuperClass(adaptersMap);
    }

    return true;
  }

  @Override public void generateCode() throws IOException {
    if (checkViewTypeIntegerValues()) {
      generateViewHolders();
      generateBinderInterface();
      generateAdapterHelper();
    }
  }

  private static class Pair<A, B> {
    A first;
    B second;

    public Pair(A a, B b) {
      first = a;
      second = b;
    }

    public static <X, Y> Pair<X, Y> create(X a, Y b) {
      return new Pair<X, Y>(a, b);
    }
  }
}
