package io.github.rkaushikdev.staplewright.persistence;

import io.github.rkaushikdev.staplewright.ConflictError;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One transaction, with every repository hanging off it.
 *
 * <p>The rule this exists to enforce is that a service does one thing and either all of it happens
 * or none of it does. Issuing a key writes the key row, takes the deposit, and marks the request
 * filled, and a crash between the second and the third leaves a key out that nobody asked for.
 *
 * <p>So a service takes a unit of work rather than a session, uses the repositories on it, and never
 * commits: {@link #within} commits when the work returns, unless the work rolled back by hand, and
 * rolls back when the work throws. Either way the session is closed on the way out, and the same
 * unit can be opened again afterwards. Nothing inside the domain has to know a transaction exists.
 */
public final class UnitOfWork {

    private final Supplier<Session> sessions;
    private Session session;
    private Repositories repositories;
    private boolean committed;
    private boolean rolledBack;

    /** A unit that opens its session from {@code sessions}, such as {@code database::session}. */
    public UnitOfWork(Supplier<Session> sessions) {
        this.sessions = sessions;
    }

    /** The way services ask for one, so the constructor can change. */
    public static UnitOfWork of(Supplier<Session> sessions) {
        return new UnitOfWork(sessions);
    }

    /**
     * Open the unit, run {@code work} in it, and close it: committing when the work returns, unless
     * it rolled back by hand, and rolling back when it throws, which then reaches the caller.
     *
     * @throws ConflictError when the unit is already open
     */
    public void within(Consumer<UnitOfWork> work) {
        from(unit -> {
            work.accept(unit);
            return null;
        });
    }

    /** What {@code work} answers, run as {@link #within} runs it. */
    public <T> T from(Function<UnitOfWork, T> work) {
        open();
        try {
            T answer;
            try {
                answer = work.apply(this);
            } catch (RuntimeException | Error trouble) {
                try {
                    rollback();
                } catch (RuntimeException alsoFailed) {
                    trouble.addSuppressed(alsoFailed);
                }
                throw trouble;
            }
            if (rolledBack) {
                rollback();
            } else {
                commit();
            }
            return answer;
        } finally {
            Session closing = session;
            session = null;
            repositories = null;
            closing.close();
        }
    }

    private void open() {
        if (session != null) {
            throw new ConflictError("this unit of work is already open");
        }
        session = sessions.get();
        committed = false;
        rolledBack = false;
        repositories = new Repositories(session);
    }

    /**
     * The session underneath, for the work a repository has no method for.
     *
     * @throws ConflictError when the unit is not open
     */
    public Session session() {
        if (session == null) {
            throw new ConflictError("this unit of work is not open");
        }
        return session;
    }

    /** Make the work so far stick. The unit stays open, and leaving it commits whatever follows. */
    public void commit() {
        session().commit();
        committed = true;
    }

    /** Undo the work since the last commit, and stop the unit committing on the way out. */
    public void rollback() {
        session().rollback();
        rolledBack = true;
    }

    /** Push writes down so keys come back, without ending the transaction. */
    public void flush() {
        session().flush();
    }

    /** Whether the unit has committed since it was last opened. */
    public boolean committed() {
        return committed;
    }

    /** Whether the unit has rolled back since it was last opened. */
    public boolean rolledBack() {
        return rolledBack;
    }

    public ChartRepository charts() {
        return opened().charts();
    }

    public SystemRepository systems() {
        return opened().systems();
    }

    public OpeningRepository openings() {
        return opened().openings();
    }

    public CylinderRepository cylinders() {
        return opened().cylinders();
    }

    public KeyholderRepository keyholders() {
        return opened().keyholders();
    }

    public KeyRepository keys() {
        return opened().keys();
    }

    public RequestRepository requests() {
        return opened().requests();
    }

    public IncidentRepository incidents() {
        return opened().incidents();
    }

    /** The work orders. */
    public WorkOrderRepository orders() {
        return opened().orders();
    }

    /** The retired cuts. */
    public QuarantineRepository quarantine() {
        return opened().quarantine();
    }

    private Repositories opened() {
        session();
        return repositories;
    }

    /** The repositories of one opening, all on its one session. */
    private record Repositories(
            ChartRepository charts,
            SystemRepository systems,
            OpeningRepository openings,
            CylinderRepository cylinders,
            KeyholderRepository keyholders,
            KeyRepository keys,
            RequestRepository requests,
            IncidentRepository incidents,
            WorkOrderRepository orders,
            QuarantineRepository quarantine) {

        Repositories(Session session) {
            this(
                    new ChartRepository(session),
                    new SystemRepository(session),
                    new OpeningRepository(session),
                    new CylinderRepository(session),
                    new KeyholderRepository(session),
                    new KeyRepository(session),
                    new RequestRepository(session),
                    new IncidentRepository(session),
                    new WorkOrderRepository(session),
                    new QuarantineRepository(session));
        }
    }
}
