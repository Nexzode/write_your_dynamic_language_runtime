package fr.umlv.smalljs.astinterp;

import fr.umlv.smalljs.ast.Expr;
import fr.umlv.smalljs.ast.Expr.Block;
import fr.umlv.smalljs.ast.Expr.FieldAccess;
import fr.umlv.smalljs.ast.Expr.FieldAssignment;
import fr.umlv.smalljs.ast.Expr.Fun;
import fr.umlv.smalljs.ast.Expr.FunCall;
import fr.umlv.smalljs.ast.Expr.If;
import fr.umlv.smalljs.ast.Expr.Literal;
import fr.umlv.smalljs.ast.Expr.LocalVarAccess;
import fr.umlv.smalljs.ast.Expr.LocalVarAssignment;
import fr.umlv.smalljs.ast.Expr.MethodCall;
import fr.umlv.smalljs.ast.Expr.New;
import fr.umlv.smalljs.ast.Expr.Return;
import fr.umlv.smalljs.ast.Script;
import fr.umlv.smalljs.rt.Failure;
import fr.umlv.smalljs.rt.JSObject;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static fr.umlv.smalljs.rt.JSObject.UNDEFINED;
import static java.util.stream.Collectors.joining;

public final class ASTInterpreter {
  private static JSObject asJSObject(Object value, int lineNumber) {
    if (!(value instanceof JSObject jsObject)) {
      throw new Failure("at line " + lineNumber + ", type error " + value + " is not a JSObject");
    }
    return jsObject;
  }

  static Object visit(Expr expression, JSObject env) {
    return switch (expression) {
      case Block(List<Expr> instrs, int lineNumber) -> {
				//throw new UnsupportedOperationException("TODO Block");
        instrs.forEach(element -> visit(element, env));
        yield UNDEFINED;
      }
      case Literal<?>(Object value, int lineNumber) -> {
        yield value;
      }
      case FunCall(Expr qualifier, List<Expr> args, int lineNumber) -> {
        var value = visit(qualifier, env);
        if(!(value instanceof JSObject jsObject)){
          throw new Failure("qualifiere not a JsonObject: line " + lineNumber);
        }
        var values = args.stream().map(e -> visit(e, env)).toArray();
        yield jsObject.invoke(UNDEFINED, values);
      }
      case LocalVarAccess(String name, int lineNumber) -> {
        yield env.lookup(name);
      }
      case LocalVarAssignment(String name, Expr expr, boolean declaration, int lineNumber) -> {
        var value = visit(expr, env);

        if(declaration && env.lookup(name) != UNDEFINED){
          throw new Failure(name + " was already declared: line " + lineNumber);
        }
        env.register(name, value);
        yield value;
      }
      case Fun(Optional<String> optName, List<String> parameters, Block body, int lineNumber) -> {
        var functionName = optName.orElse("lambda");
        JSObject.Invoker invoker = new JSObject.Invoker() {
        @Override
        public Object invoke(Object receiver, Object... args) {
        //    // check the arguments length
          if(args.length != parameters.size() ){
            throw new Failure("Wrong number of arguments");
          }
        //    // create a new environment
          var jsonObject = JSObject.newEnv(env);
        //    // add this and all the parameters
          jsonObject.register("this", receiver);
          for(var i = 0; i < args.length; i++){
            jsonObject.register(parameters.get(i), args[i]);
          }
        //    // visit the body
          try {
            return visit(body, jsonObject);
          }catch (ReturnError e){
            return e.getValue();
          }
        }
        };
        // create the JS function with the invoker
        var function = env.newFunction(functionName, invoker);
        // register it if necessary
        optName.ifPresent( name -> env.register(name, function));
        // yield the function
        yield function;
      }
      case Return(Expr expr, int lineNumber) -> {
        var value = visit(expr, env);
        throw new ReturnError(value);
      }
      case If(Expr condition, Block trueBlock, Block falseBlock, int lineNumber) -> {
        var value = visit(condition, env);
        if(value instanceof Integer integer && integer == 0){
          visit(falseBlock, env);
        }
        visit(trueBlock, env);
        yield UNDEFINED;
      }
      case New(Map<String, Expr> initMap, int lineNumber) -> {
        initMap.forEach(env::register);
        yield UNDEFINED;//Fun();
      }
      case FieldAccess(Expr receiver, String name, int lineNumber) -> {
        throw new UnsupportedOperationException("TODO FieldAccess");
      }
      case FieldAssignment(Expr receiver, String name, Expr expr, int lineNumber) -> {
        throw new UnsupportedOperationException("TODO FieldAssignment");
      }
      case MethodCall(Expr receiver, String name, List<Expr> args, int lineNumber) -> {
        throw new UnsupportedOperationException("TODO MethodCall");
      }
    };
  }

  @SuppressWarnings("unchecked")
  private static JSObject createGlobalEnv(PrintStream outStream) {
    JSObject globalEnv = JSObject.newEnv(null);
    globalEnv.register("global", globalEnv);
    globalEnv.register("print", JSObject.newFunction("print", (_, args) -> {
      System.err.println("print called with " + Arrays.toString(args));
      outStream.println(Arrays.stream(args).map(Object::toString).collect(Collectors.joining(" ")));
      return UNDEFINED;
    }));
    globalEnv.register("+", JSObject.newFunction("+", (_, args) -> (Integer) args[0] + (Integer) args[1]));
    globalEnv.register("-", JSObject.newFunction("-", (_, args) -> (Integer) args[0] - (Integer) args[1]));
    globalEnv.register("/", JSObject.newFunction("/", (_, args) -> (Integer) args[0] / (Integer) args[1]));
    globalEnv.register("*", JSObject.newFunction("*", (_, args) -> (Integer) args[0] * (Integer) args[1]));
    globalEnv.register("%", JSObject.newFunction("%", (_, args) -> (Integer) args[0] % (Integer) args[1]));
    globalEnv.register("==", JSObject.newFunction("==", (_, args) -> args[0].equals(args[1]) ? 1 : 0));
    globalEnv.register("!=", JSObject.newFunction("!=", (_, args) -> !args[0].equals(args[1]) ? 1 : 0));
    globalEnv.register("<", JSObject.newFunction("<", (_, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) < 0) ? 1 : 0));
    globalEnv.register("<=", JSObject.newFunction("<=", (_, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) <= 0) ? 1 : 0));
    globalEnv.register(">", JSObject.newFunction(">", (_, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) > 0) ? 1 : 0));
    globalEnv.register(">=", JSObject.newFunction(">=", (_, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) >= 0) ? 1 : 0));
    return globalEnv;
  }

  public static void interpret(Script script, PrintStream outStream) {
    JSObject globalEnv =createGlobalEnv(outStream);
    Block body = script.body();
    visit(body, globalEnv);
  }
}

