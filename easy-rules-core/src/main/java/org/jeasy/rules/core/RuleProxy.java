/**
 * The MIT License
 *
 *  Copyright (c) 2019, Mahmoud Ben Hassine (mahmoud.benhassine@icloud.com)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package org.jeasy.rules.core;

import org.jeasy.rules.annotation.Action;
import org.jeasy.rules.annotation.Condition;
import org.jeasy.rules.annotation.Fact;
import org.jeasy.rules.annotation.Priority;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Main class to create rule proxies from annotated objects.
 *
 * @author Mahmoud Ben Hassine (mahmoud.benhassine@icloud.com)
 */
public class RuleProxy implements InvocationHandler {

    private Object target;

    private static RuleDefinitionValidator ruleDefinitionValidator = new RuleDefinitionValidator();

    private static final Logger LOGGER = LoggerFactory.getLogger(RuleProxy.class);

    private RuleProxy(final Object target) {
        this.target = target;
    }

    /**
     * Makes the rule object implement the {@link Rule} interface.
     *
     * @param rule the annotated rule object.
     * @return a proxy that implements the {@link Rule} interface.
     */
    public static Rule asRule(final Object rule) {
        if (rule instanceof Rule) {
            return (Rule) rule;
        } else {
            ruleDefinitionValidator.validateRuleDefinition(rule);
            return (Rule) Proxy.newProxyInstance(
                    Rule.class.getClassLoader(),
                    new Class[]{Rule.class, Comparable.class},
                    new RuleProxy(rule));
        }
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        String methodName = method.getName();
        switch (methodName) {
            case "getName":
                return getRuleName();
            case "getDescription":
                return getRuleDescription();
            case "getPriority":
                return getRulePriority();
            case "compareTo":
                return compareToMethod(args);
            case "evaluate":
                return evaluateMethod(args);
            case "accept":
                return executeMethod(args);
            case "equals":
                return equalsMethod(args);
            case "hashCode":
                return hashCodeMethod();
            case "toString":
                return toStringMethod();
            default:
                return null;
        }
    }

    private Object evaluateMethod(final Object[] args) throws IllegalAccessException, InvocationTargetException {
        Facts facts = (Facts) args[0];
        Method conditionMethod = getConditionMethod().orElse(null);
        try {
            List<Object> actualParameters = getActualParameters(conditionMethod, facts);
            return conditionMethod.invoke(target, actualParameters.toArray()); // validated upfront
        } catch (NoSuchFactException e) {
            LOGGER.error("Rule '{}' has been evaluated to false due to a declared but missing fact '{}' in {}",
                    getTargetClass().getName(), e.getMissingFact(), facts);
            return false;
        } catch (IllegalArgumentException e) {
            String error = "Types of injected facts in method '%s' in rule '%s' do not match parameters types";
            throw new RuntimeException(format(error, conditionMethod.getName(), getTargetClass().getName()), e);
        }
    }

    private Object executeMethod(final Object[] args) throws IllegalAccessException, InvocationTargetException {
        Facts facts = (Facts) args[0];
        for (ActionMethodOrderBean actionMethodBean : getActionMethodBeans()) {
            Method actionMethod = actionMethodBean.getMethod();
            List<Object> actualParameters = getActualParameters(actionMethod, facts);
            actionMethod.invoke(target, actualParameters.toArray());
        }
        return null;
    }

    private Object compareToMethod(final Object[] args) throws InvocationTargetException, IllegalAccessException {
        Optional<Method> compareToMethod = getCompareToMethod();
        if (compareToMethod.isPresent()) {
            return compareToMethod.get().invoke(target, args);
        } else {
            Rule otherRule = (Rule) args[0];
            return compareTo(otherRule);
        }
    }

    private List<Object> getActualParameters(Method method, Facts facts) {
        List<Object> actualParameters = new ArrayList<>();
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        for (Annotation[] annotations : parameterAnnotations) {
            if (annotations.length == 1) {
                String factName = ((Fact) (annotations[0])).value(); //validated upfront.
                Object fact = facts.get(factName);
                if (fact == null && !facts.asMap().containsKey(factName)) {
                    throw new NoSuchFactException(format("No fact named '%s' found in known facts: %n%s", factName, facts), factName);
                }
                actualParameters.add(fact);
            } else {
                actualParameters.add(facts); //validated upfront, there may be only one parameter not annotated and which is of type Facts.class
            }
        }
        return actualParameters;
    }

    private boolean equalsMethod(final Object[] args) throws Exception {
        if (!(args[0] instanceof Rule)) {
            return false;
        }
        Rule otherRule = (Rule) args[0];
        int otherPriority = otherRule.getPriority();
        int priority = getRulePriority();
        if (priority != otherPriority) {
            return false;
        }
        String otherName = otherRule.getName();
        String name = getRuleName();
        if (!name.equals(otherName)) {
            return false;
        }
        String otherDescription = otherRule.getDescription();
        String description = getRuleDescription();
        return Objects.equals(description, otherDescription);
    }

    private int hashCodeMethod() throws InvocationTargetException, IllegalAccessException {
        int result = getRuleName().hashCode();
        int priority = getRulePriority();
        String description = getRuleDescription();
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + priority;
        return result;
    }

    private String toStringMethod() throws Exception {
        Method[] methods = getMethods();
        for (Method method : methods) {
            if ("toString".equals(method.getName())) {
                return (String) method.invoke(target);
            }
        }
        return getRuleName();
    }

    private int compareTo(final Rule otherRule) throws InvocationTargetException, IllegalAccessException {
        int otherPriority = otherRule.getPriority();
        int priority = getRulePriority();
        if (priority < otherPriority) {
            return -1;
        } else if (priority > otherPriority) {
            return 1;
        } else {
            String otherName = otherRule.getName();
            String name = getRuleName();
            return name.compareTo(otherName);
        }
    }

    private int getRulePriority() throws InvocationTargetException, IllegalAccessException {
        int priority = Rule.DEFAULT_PRIORITY;

        org.jeasy.rules.annotation.Rule rule = getRuleAnnotation();
        if (rule.priority() != Rule.DEFAULT_PRIORITY) {
            priority = rule.priority();
        }

        Method[] methods = getMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(Priority.class)) {
                priority = (int) method.invoke(target);
                break;
            }
        }
        return priority;
    }

    private Optional<Method> getConditionMethod() {
        return Arrays.stream(getMethods())
                .filter(method -> method.isAnnotationPresent(Condition.class))
                .findFirst();
    }

    private Set<ActionMethodOrderBean> getActionMethodBeans() {
        return Arrays.stream(getMethods())
                .filter(method -> method.isAnnotationPresent(Action.class))
                .map(method -> new ActionMethodOrderBean(method, method.getAnnotation(Action.class).order()))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private Optional<Method> getCompareToMethod() {
        return Arrays.stream(getMethods())
                .filter(method -> Objects.equals(method.getName(), "compareTo"))
                .findFirst();
    }

    private Method[] getMethods() {
        return getTargetClass().getMethods();
    }

    private org.jeasy.rules.annotation.Rule getRuleAnnotation() {
        return Utils.findAnnotation(org.jeasy.rules.annotation.Rule.class, getTargetClass()).orElse(null);
    }

    private String getRuleName() {
        org.jeasy.rules.annotation.Rule rule = getRuleAnnotation();
        return rule.name().equals(Rule.DEFAULT_NAME) ? getTargetClass().getSimpleName() : rule.name();
    }

    private String getRuleDescription() {
        // Default description = "when " + conditionMethodName + " then " + comma separated actionMethodsNames
        StringBuilder description = new StringBuilder();
        appendConditionMethodName(description);
        appendActionMethodsNames(description);

        org.jeasy.rules.annotation.Rule rule = getRuleAnnotation();
        return rule.description().equals(Rule.DEFAULT_DESCRIPTION) ? description.toString() : rule.description();
    }

    private void appendConditionMethodName(StringBuilder description) {
        getConditionMethod().ifPresent(method -> {
            description.append("when ");
            description.append(method.getName());
            description.append(" then ");
        });
    }

    private void appendActionMethodsNames(StringBuilder description) {
        String methodNames = getActionMethodBeans().stream()
                .map(methodBean -> methodBean.getMethod().getName())
                .collect(Collectors.joining(","));
        description.append(methodNames);
    }

    private Class<?> getTargetClass() {
        return target.getClass();
    }

}
