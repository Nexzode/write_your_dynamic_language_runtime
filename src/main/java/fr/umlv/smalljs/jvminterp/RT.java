package fr.umlv.smalljs.jvminterp;

import static fr.umlv.smalljs.rt.JSObject.UNDEFINED;
import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;

import fr.umlv.smalljs.rt.JSObject;


public final class RT {
  private static final MethodHandle LOOKUP, REGISTER, INVOKE, TRUTH, METH_LOOKUP_MH;
  static {
    var lookup = MethodHandles.lookup();
    try {
      LOOKUP = lookup.findVirtual(JSObject.class, "lookup", methodType(Object.class, String.class));
      REGISTER = lookup.findVirtual(JSObject.class, "register", methodType(void.class, String.class, Object.class));

      INVOKE = lookup.findVirtual(JSObject.class, "invoke", methodType(Object.class, Object.class, Object[].class));

      TRUTH = lookup.findStatic(RT.class, "truth", methodType(boolean.class, Object.class));

      METH_LOOKUP_MH = lookup.findStatic(RT.class, "lookupMethodHandle", methodType(MethodHandle.class, JSObject.class, String.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  public static Object bsm_undefined(Lookup lookup, String name, Class<?> type) {
    return UNDEFINED;
  }

  public static Object bsm_const(Lookup lookup, String name, Class<?> type, int constant) {
    return constant;
  }

  public static CallSite bsm_lookup(Lookup lookup, String name, MethodType type, String functionName) {
    var classLoader = (FunClassLoader) lookup.lookupClass().getClassLoader();
    var globalEnv = classLoader.getGlobal();
    // get the LOOKUP method handle
    var mh = LOOKUP;
    // use the global environment as first argument and the functionName as second argument
    mh = MethodHandles.insertArguments(mh, 0, globalEnv, functionName);
    // create a constant callsite
    return new ConstantCallSite(mh);
  }

  public static CallSite bsm_funcall(Lookup lookup, String name, MethodType type) {
    return new InliningCache(type);
  }

  private static class InliningCache extends MutableCallSite {
    private static final MethodHandle SLOW_PATH;
    private static final MethodHandle TEST;

    static {
      var lookup = MethodHandles.lookup();
      try {
        SLOW_PATH = lookup.findVirtual(InliningCache.class, "slowPath", methodType(MethodHandle.class, Object.class, Object.class));
        TEST = lookup.findStatic(InliningCache.class, "test", methodType(boolean.class, Object.class, Object.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }

    public InliningCache(MethodType type) {
      super(type);
      setTarget(MethodHandles.foldArguments(MethodHandles.exactInvoker(type), SLOW_PATH.bindTo(this)));
    }

    private static boolean test(Object receiver, Object previousQualifier){
      return receiver == previousQualifier;
    }

    private MethodHandle slowPath(Object qualifier, Object receiver) {
      var jsObject = (JSObject)qualifier;
      var mh = jsObject.getMethodHandle();
      var varargs = mh.isVarargsCollector();

      System.err.println("arguments " + (type().parameterCount() - 2));
      System.err.println("parameters " + (mh.type().parameterCount() - 1));

      mh = MethodHandles.dropArguments(mh, 0, Object.class);
      mh = mh.withVarargs(varargs);
      mh = mh.asType(type());

      System.err.println("resolved " + jsObject);

//      var test = MethodHandles.insertArguments(TEST, 1, qualifier);
//      var guard = MethodHandles.guardWithTest(test, mh, getTarget());

      var test = MethodHandles.insertArguments(TEST, 1, qualifier);
      var fallback = new InliningCache(type()).dynamicInvoker();
      var guard = MethodHandles.guardWithTest(test, mh, fallback);

      setTarget(guard);
      return mh;
    }
  }

  public static Object bsm_fun(Lookup lookup, String name, Class<?> type, int funId) {
    var classLoader = (FunClassLoader) lookup.lookupClass().getClassLoader();
    var globalEnv = classLoader.getGlobal();
    var fun = classLoader.getDictionary().lookupAndClear(funId);
    return ByteCodeRewriter.createFunction(fun.optName().orElse("lambda"), fun.parameters(), fun.body(), globalEnv);
  }

  public static CallSite bsm_register(Lookup lookup, String name, MethodType type, String functionName) {
    var classLoader = (FunClassLoader) lookup.lookupClass().getClassLoader();
    var globalEnv = classLoader.getGlobal();
    //get the REGISTER method handle
    var mh = REGISTER;
    // use the global environment as first argument and the functionName as second argument
    mh = MethodHandles.insertArguments(mh, 0, globalEnv, functionName);
    // create a constant callsite
    return new ConstantCallSite(mh);
  }

  @SuppressWarnings("unused")  // used by a method handle
  private static boolean truth(Object o) {
    return o != null && o != UNDEFINED && o != Boolean.FALSE;
  }
  public static CallSite bsm_truth(Lookup lookup, String name, MethodType type) {
    // get the TRUTH method handle
    var mh = TRUTH;
    // create a constant callsite
    return  new ConstantCallSite(mh);
  }

  public static CallSite bsm_get(Lookup lookup, String name, MethodType type, String fieldName) {
    throw new UnsupportedOperationException("TODO bsm_get");
    // get the LOOKUP method handle
    // use the fieldName as second argument
    // make it accept an Object (not a JSObject) as first parameter
    // create a constant callsite
  }

  public static CallSite bsm_set(Lookup lookup, String name, MethodType type, String fieldName) {
    throw new UnsupportedOperationException("TODO bsm_set");
    // get the REGISTER method handle
    // use the fieldName as second argument
    // make it accept an Object (not a JSObject) as first parameter
    // create a constant callsite
  }

  @SuppressWarnings("unused")  // used by a method handle
  private static MethodHandle lookupMethodHandle(JSObject receiver, String fieldName) {
    var function = (JSObject) receiver.lookup(fieldName);
    return function.getMethodHandle();
  }

  public static CallSite bsm_methodcall(Lookup lookup, String name, MethodType type) {
    throw new UnsupportedOperationException("TODO bsm_methodcall");
    //var combiner = insertArguments(METH_LOOKUP_MH, 1, name).asType(methodType(MethodHandle.class, Object.class));
    //var target = foldArguments(invoker(type), combiner);
    //return new ConstantCallSite(target);
  }
}
