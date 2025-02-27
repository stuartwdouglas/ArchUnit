/*
 * Copyright 2014-2022 TNG Technology Consulting GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tngtech.archunit.lang;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.reflect.TypeToken;
import com.tngtech.archunit.PublicAPI;
import com.tngtech.archunit.base.Optional;

import static com.tngtech.archunit.PublicAPI.State.EXPERIMENTAL;
import static com.tngtech.archunit.PublicAPI.Usage.ACCESS;

public final class ConditionEvents implements Iterable<ConditionEvent> {

    @PublicAPI(usage = ACCESS)
    public ConditionEvents() {
    }

    private final Multimap<Type, ConditionEvent> eventsByViolation = ArrayListMultimap.create();
    private Optional<String> informationAboutNumberOfViolations = Optional.empty();

    @PublicAPI(usage = ACCESS)
    public void add(ConditionEvent event) {
        eventsByViolation.get(Type.from(event.isViolation())).add(event);
    }

    /**
     * Can be used to override the information about the number of violations. If absent the violated rule
     * will simply report the number of violation lines as the number of violations (which is typically
     * correct, since ArchUnit usually reports one violation per line). However in cases where
     * violations are omitted (e.g. because a limit of reported violations is configured), this information
     * can be supplied here to inform users there there actually were more violations than reported.
     * @param informationAboutNumberOfViolations The text to be shown for the number of times a rule has been violated
     */
    @PublicAPI(usage = ACCESS)
    public void setInformationAboutNumberOfViolations(String informationAboutNumberOfViolations) {
        this.informationAboutNumberOfViolations = Optional.of(informationAboutNumberOfViolations);
    }

    @PublicAPI(usage = ACCESS)
    public Collection<ConditionEvent> getViolating() {
        return eventsByViolation.get(Type.VIOLATION);
    }

    @PublicAPI(usage = ACCESS)
    public Collection<ConditionEvent> getAllowed() {
        return eventsByViolation.get(Type.ALLOWED);
    }

    @PublicAPI(usage = ACCESS)
    public boolean containViolation() {
        return !getViolating().isEmpty();
    }

    @PublicAPI(usage = ACCESS)
    public boolean isEmpty() {
        return getAllowed().isEmpty() && getViolating().isEmpty();
    }

    /**
     * @deprecated Use {@link #getFailureMessages()} instead
     * @return List of text lines describing the contained failures of these events.
     */
    @Deprecated
    @PublicAPI(usage = ACCESS)
    public List<String> getFailureDescriptionLines() {
        return getFailureMessages();
    }

    /**
     * @return Sorted failure messages describing the contained failures of these events.
     *         Also offers information about the number of violations contained in these events.
     */
    @PublicAPI(usage = ACCESS)
    public FailureMessages getFailureMessages() {
        ImmutableList<String> result = FluentIterable.from(getViolating())
                .transformAndConcat(TO_DESCRIPTION_LINES)
                .toSortedList(Ordering.natural());
        return new FailureMessages(result, informationAboutNumberOfViolations);
    }

    /**
     * Passes violations to the supplied {@link ViolationHandler}. The passed violations will automatically
     * be filtered by the reified type of the given {@link ViolationHandler}. That is, if a
     * <code>ViolationHandler&lt;SomeClass&gt;</code> is passed, only violations by objects assignable to
     * <code>SomeClass</code> will be reported. The term 'reified' means that the type parameter
     * was not erased, i.e. ArchUnit can still determine the actual type parameter of the passed violation handler,
     * otherwise the upper bound, in extreme cases {@link Object}, will be used (i.e. all violations will be passed).
     *
     * @param violationHandler The violation handler that is supposed to handle all violations matching the
     *                         respective type parameter
     */
    @PublicAPI(usage = ACCESS, state = EXPERIMENTAL)
    public void handleViolations(ViolationHandler<?> violationHandler) {
        ConditionEvent.Handler eventHandler = convertToEventHandler(violationHandler);
        for (final ConditionEvent event : eventsByViolation.get(Type.VIOLATION)) {
            event.handleWith(eventHandler);
        }
    }

    private <T> ConditionEvent.Handler convertToEventHandler(final ViolationHandler<T> handler) {
        final Class<?> supportedElementType = TypeToken.of(handler.getClass())
                .resolveType(ViolationHandler.class.getTypeParameters()[0]).getRawType();

        return new ConditionEvent.Handler() {
            @Override
            public void handle(Collection<?> correspondingObjects, String message) {
                if (allElementTypesMatch(correspondingObjects, supportedElementType)) {
                    // If all elements are assignable to T (= supportedElementType), covariance of Collection allows this cast
                    @SuppressWarnings("unchecked")
                    Collection<T> collection = (Collection<T>) correspondingObjects;
                    handler.handle(collection, message);
                }
            }
        };
    }

    private boolean allElementTypesMatch(Collection<?> violatingObjects, Class<?> supportedElementType) {
        for (Object violatingObject : violatingObjects) {
            if (!supportedElementType.isInstance(violatingObject)) {
                return false;
            }
        }
        return true;
    }

    @Override
    @PublicAPI(usage = ACCESS)
    public Iterator<ConditionEvent> iterator() {
        return ImmutableSet.copyOf(eventsByViolation.values()).iterator();
    }

    @Override
    public String toString() {
        return "ConditionEvents{" +
                "Allowed Events: " + getAllowed() +
                "; Violating Events: " + getViolating() +
                '}';
    }

    private static final Function<ConditionEvent, Iterable<String>> TO_DESCRIPTION_LINES = new Function<ConditionEvent, Iterable<String>>() {
        @Override
        public Iterable<String> apply(ConditionEvent input) {
            return input.getDescriptionLines();
        }
    };

    private enum Type {
        ALLOWED, VIOLATION;

        private static Type from(boolean violation) {
            return violation ? VIOLATION : ALLOWED;
        }
    }
}
