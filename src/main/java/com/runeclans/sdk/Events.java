package com.runeclans.sdk;

import com.runeclans.sdk.model.Event;
import com.runeclans.sdk.model.EventBoard;
import com.runeclans.sdk.model.EventList;
import com.runeclans.sdk.model.EventParticipants;
import com.runeclans.sdk.model.EventStandingsAnswer;

import java.util.Optional;

/** Clan events (competitions, bingos, signups). Read-only for outside apps; clan events are listed per clan in {@link Clans#events}. */
public final class Events {
    private final RuneClanClient c;

    Events(RuneClanClient c) {
        this.c = c;
    }

    /** Events shared beyond their clan. {@code scope} public (default) or alliance; {@code from}/{@code to} ISO-8601; any may be null. */
    public ApiAction<EventList> shared(String scope, String from, String to) {
        return c.route(Routes.GET_EVENTS).query("scope", scope).query("from", from).query("to", to).as(EventList.class);
    }

    public ApiAction<Optional<Event>> get(String eventId) {
        return c.route(Routes.GET_EVENTS_EVENT_ID).path("eventId", eventId).asOptional(Event.class);
    }

    public ApiAction<EventParticipants> participants(String eventId) {
        return c.route(Routes.GET_EVENTS_EVENT_ID_PARTICIPANTS).path("eventId", eventId).as(EventParticipants.class);
    }

    public ApiAction<EventStandingsAnswer> standings(String eventId) {
        return c.route(Routes.GET_EVENTS_EVENT_ID_STANDINGS).path("eventId", eventId).as(EventStandingsAnswer.class);
    }

    /** A bingo or board event's board. */
    public ApiAction<EventBoard> board(String eventId) {
        return c.route(Routes.GET_EVENTS_EVENT_ID_BOARD).path("eventId", eventId).as(EventBoard.class);
    }

    /** The board as a PNG picture. */
    public ApiAction<byte[]> boardPng(String eventId) {
        return c.route(Routes.GET_EVENTS_EVENT_ID_BOARD_PNG).path("eventId", eventId).asBytes();
    }
}
