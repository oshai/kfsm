/*
 * Copyright (c) 2019. Open JumpCO
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.jumpco.open.kfsm

/**
 * This class represents the definition of a statemachine.
 * @param S is an enum representing all the states of the FSM
 * @param E is en enum representing all the events the FSM may receive
 * @param C is the class of the Context where the action will be applied.
 */
class StateMachineBuilder<S : Enum<S>, E : Enum<E>, C> {
    private var completed = false
    private var deriveInitialState: StateQuery<C, S>? = null
    private val transitionRules: MutableMap<Pair<S, E>, TransitionRules<S, E, C>> = mutableMapOf()
    private val defaultTransitions: MutableMap<E, DefaultTransition<E, S, C>> = mutableMapOf()
    private val entryActions: MutableMap<S, DefaultChangeAction<C, S>> = mutableMapOf()
    private val exitActions: MutableMap<S, DefaultChangeAction<C, S>> = mutableMapOf()
    private val defaultActions: MutableMap<S, DefaultStateAction<C, S, E>> = mutableMapOf()
    private var globalDefault: DefaultStateAction<C, S, E>? = null
    private var defaultEntryAction: DefaultChangeAction<C, S>? = null
    private var defaultExitAction: DefaultChangeAction<C, S>? = null

    /**
     * This function defines a transition from the currentState equal to startState to the targetState when event is
     * received and the guard expression is met. The action is executed after any exit action and before entry actions.
     * @param startState The transition will be considered when currentState matches stateState
     * @param event The event will trigger the consideration of this transition
     * @param targetState The transition will change currentState to targetState after executing the option action
     * @param guard The guard expression will have to be met to consider the transition
     * @param action The optional action will be executed when the transition occurs.
     */
    fun transition(startState: S, event: E, targetState: S, guard: StateGuard<C>, action: StateAction<C>?) {
        require(!completed) { "Statemachine has been completed" }
        val key = Pair(startState, event)
        val transitionRule = transitionRules[key]
        if (transitionRule == null) {
            val rule = TransitionRules<S, E, C>()
            rule.addGuarded(GuardedTransition(startState, event, targetState, guard, action))
            transitionRules[key] = rule
        } else {
            transitionRule.addGuarded(GuardedTransition(startState, event, targetState, guard, action))
        }
    }

    /**
     * This function defines a transition that doesn't change the state also know as an internal transition.
     * The transition will only occur if the guard expression is met.
     * @param startState The transition will be considered when currentState matches stateState
     * @param event The event will trigger the consideration of this transition
     * @param guard The guard expression will have to be met to consider the transition
     * @param action The optional action will be executed when the transition occurs.
     */
    fun transition(startState: S, event: E, guard: StateGuard<C>, action: StateAction<C>?) {
        require(!completed) { "Statemachine has been completed" }
        val key = Pair(startState, event)
        val transitionRule = transitionRules[key]
        if (transitionRule == null) {
            val rule = TransitionRules<S, E, C>()
            rule.addGuarded(GuardedTransition(startState, event, null, guard, action))
            transitionRules[key] = rule
        } else {
            transitionRule.addGuarded(GuardedTransition(startState, event, null, guard, action))
        }
    }


    /**
     * This function defines a transition that will be triggered when the currentState is the same as the startState and on is received. The FSM currentState will change to the targetState after the action was executed.
     * Entry and Exit actions will also be performed.
     * @param startState transition applies when FSM currentState is the same as stateState.
     * @param event transition applies when on received.
     * @param targetState FSM will transition to targetState.
     * @param action The actions will be invoked
     */
    fun transition(startState: S, event: E, targetState: S, action: StateAction<C>?) {
        require(!completed) { "Statemachine has been completed" }
        val key = Pair(startState, event)
        val transitionRule = transitionRules[key]
        if (transitionRule == null) {
            transitionRules[key] =
                TransitionRules(transition = SimpleTransition(startState, event, targetState, action))
        } else {
            require(transitionRule.transition == null) { "Unguarded Transition for $startState on $event already defined" }
            transitionRule.transition = SimpleTransition(startState, event, targetState, action)
        }
    }

    /**
     * This function defines a transition that doesn't change the state of the state machine when the currentState is startState and the on is received and after the action was performed. No entry or exit actions performed.
     * @param startState transition applies when when FSM currentState is the same as stateState
     * @param event transition applies when on received
     * @param action actions will be invoked
     */
    fun transition(startState: S, event: E, action: StateAction<C>?) {
        require(!completed) { "Statemachine has been completed" }
        val key = Pair(startState, event)
        val transitionRule = transitionRules[key]
        if (transitionRule == null) {
            transitionRules[key] = TransitionRules(
                transition = SimpleTransition(
                    startState,
                    event,
                    null,
                    action
                )
            )
        } else {
            require(transitionRule.transition == null) { "Unguarded Transition for $startState on $event already defined" }
            transitionRule.transition = SimpleTransition(startState, event, null, action)
        }

    }


    /**
     * This function defines an action to be invoked when no action is found matching the current state and event.
     * This will be an internal transition and will not cause a change in state or invoke entry or exit functions.
     * @param action This action will be performed
     */
    fun defaultAction(action: DefaultStateAction<C, S, E>) {
        require(!completed) { "Statemachine has been completed" }
        globalDefault = action
    }

    /**
     * This method is the entry point to creating the DSL
     * @sample io.jumpco.open.kfsm.TurnstileFSM.definition
     */
    inline fun stateMachine(handler: DslStateMachineHandler<S, E, C>.() -> Unit): DslStateMachineHandler<S, E, C> =
        DslStateMachineHandler(this).apply(handler)

    /**
     * This function defines an action to be invoked when no entry action is defined for the current state.
     * @param action This action will be invoked
     */
    fun defaultEntry(action: DefaultChangeAction<C, S>) {
        require(!completed) { "Statemachine has been completed" }
        defaultEntryAction = action
    }

    /**
     * This function defines an action to be invoked when no exit action is defined for the current state.
     * @param action This action will be invoked
     */
    fun defaultExit(action: DefaultChangeAction<C, S>) {
        require(!completed) { "Statemachine has been completed" }
        defaultExitAction = action
    }

    /**
     * This function defines an action to be invoked when no transitions are found matching the given state and on.
     * @param currentState The provided state
     * @param action This action will be invoked
     */
    fun default(currentState: S, action: DefaultStateAction<C, S, E>) {
        require(!completed) { "Statemachine has been completed" }
        require(defaultActions[currentState] == null) { "Default defaultAction already defined for $currentState" }
        defaultActions[currentState] = action
    }

    /**
     * This function defines an action to be invoked when no transitions match the event. The currentState will be change to second parameter of the Pair.
     * @param event The Pair holds the event and targetState and can be written as `event to state`
     * @param action The option action will be executed when this default transition occurs.
     */
    fun default(event: EventState<E, S>, action: StateAction<C>?) {
        require(!completed) { "Statemachine has been completed" }
        require(defaultTransitions[event.first] == null) { "Default transition for ${event.first} already defined" }
        defaultTransitions[event.first] = DefaultTransition(event.first, event.second, action)
    }

    /**
     * This function defines an action to be invoked when no transitions are found for given event.
     * @param event The event to match this transition.
     * @param action The option action will be executed when this default transition occurs.
     */
    fun default(event: E, action: StateAction<C>?) {
        require(!completed) { "Statemachine has been completed" }
        require(defaultTransitions[event] == null) { "Default transition for $event already defined" }
        defaultTransitions[event] = DefaultTransition<E, S, C>(event, null, action)
    }

    /**
     * This function defines an action to be invoked when the FSM changes to the provided state
     * @param currentState The provided state
     * @param action This action will be invoked
     */
    fun entry(currentState: S, action: DefaultChangeAction<C, S>) {
        require(!completed) { "Statemachine has been completed" }
        require(entryActions[currentState] == null) { "Entry defaultAction already defined for $currentState" }
        entryActions[currentState] = action
    }

    /**
     * This function defines an action to be invoke when the FSM changes from the provided state
     * @param currentState The provided state
     * @param action This action will be invoked
     */
    fun exit(currentState: S, action: DefaultChangeAction<C, S>) {
        require(!completed) { "Statemachine has been completed" }
        require(exitActions[currentState] == null) { "Exit defaultAction already defined for $currentState" }
        exitActions[currentState] = action
    }

    /**
     * This function is used to provide a method for determining the initial state of the FSM using the provided content.
     * @param init Is a function that receives a context and returns the state that represents the context
     */
    fun initial(init: StateQuery<C, S>) {
        require(!completed) { "Statemachine has been completed" }
        deriveInitialState = init
    }

    /**
     * This function enables completed for the state machine definition prevent further changes to the state
     * machine behaviour.
     */
    fun complete(): StateMachineDefinition<S, E, C> {
        completed = true
        return StateMachineDefinition(
            this.deriveInitialState,
            this.transitionRules.toMap(),
            this.defaultTransitions.toMap(),
            this.entryActions.toMap(),
            this.exitActions.toMap(),
            this.defaultActions.toMap(),
            this.globalDefault,
            this.defaultEntryAction,
            this.defaultExitAction
        )
    }
}
