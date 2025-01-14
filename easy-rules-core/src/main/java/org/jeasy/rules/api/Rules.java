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
package org.jeasy.rules.api;

import org.jeasy.rules.core.RuleProxy;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class encapsulates a set of rules and represents a rules namespace.
 * Rules must have a unique name within a rules namespace.
 *
 * @author Mahmoud Ben Hassine (mahmoud.benhassine@icloud.com)
 */
public class Rules implements Iterable<Rule> {

    private Set<Rule> rules = new TreeSet<>();

    /**
     * Create a new {@link Rules} object.
     *
     * @param rules to register
     */
    public Rules(Set<Rule> rules) {
        this.rules = new TreeSet<>(rules);
    }

    /**
     * Create a new {@link Rules} object.
     *
     * @param rules to register
     */
    public Rules(Rule... rules) {
        Collections.addAll(this.rules, rules);
    }

    /**
     * Create a new {@link Rules} object.
     *
     * @param rules to register
     */
    public Rules(Object... rules) {
        Arrays.stream(rules).forEach(rule -> this.register(RuleProxy.asRule(rule)));
    }

    /**
     * Register a new rule.
     *
     * @param rule to register
     */
    public void register(Object rule) {
        Objects.requireNonNull(rule);
        rules.add(RuleProxy.asRule(rule));
    }

    /**
     * Unregister a rule.
     *
     * @param rule to unregister
     */
    public void unregister(Object rule) {
        Objects.requireNonNull(rule);
        rules.remove(RuleProxy.asRule(rule));
    }

    /**
     * Unregister a rule by name.
     *
     * @param ruleName the name of the rule to unregister
     */
    public void unregister(final String ruleName) {
        Objects.requireNonNull(ruleName);
        findRuleByName(ruleName).ifPresent(this::unregister);
    }

    /**
     * Check if the rule set is empty.
     *
     * @return true if the rule set is empty, false otherwise
     */
    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /**
     * Clear rules.
     */
    public void clear() {
        rules.clear();
    }

    @Override
    public Iterator<Rule> iterator() {
        return rules.iterator();
    }

    public Stream<Rule> asStream() {
        return rules.stream();
    }

    private Optional<Rule> findRuleByName(String ruleName) {
        return rules.stream()
                .filter(rule -> rule.getName().equalsIgnoreCase(ruleName))
                .findFirst();
    }
}
