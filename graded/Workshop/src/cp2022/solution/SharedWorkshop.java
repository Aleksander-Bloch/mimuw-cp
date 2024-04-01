package cp2022.solution;

import cp2022.base.Workplace;
import cp2022.base.WorkplaceId;
import cp2022.base.Workshop;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class SharedWorkshop implements Workshop {
    private final ConcurrentHashMap<WorkplaceId, Semaphore> guardAssignedTo;
    private final ConcurrentHashMap<Thread, WorkplaceId> workplaceOccupiedBy;
    private final ConcurrentHashMap<WorkplaceId, Workplace> idToWorkplace;

    public SharedWorkshop(ConcurrentHashMap<WorkplaceId, Semaphore> guardAssignedTo,
                          ConcurrentHashMap<Thread, WorkplaceId> workplaceOccupiedBy,
                          ConcurrentHashMap<WorkplaceId, Workplace> idToWorkplace) {
        this.guardAssignedTo = guardAssignedTo;
        this.workplaceOccupiedBy = workplaceOccupiedBy;
        this.idToWorkplace = idToWorkplace;
    }

    @Override
    public Workplace enter(WorkplaceId wid) {
        try {
            guardAssignedTo.get(wid).acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }
        workplaceOccupiedBy.put(Thread.currentThread(), wid);
        return idToWorkplace.get(wid);
    }

    @Override
    public Workplace switchTo(WorkplaceId wid) {
        leave();
        return enter(wid);
    }

    @Override
    public void leave() {
        WorkplaceId currentWid = workplaceOccupiedBy.get(Thread.currentThread());
        workplaceOccupiedBy.remove(Thread.currentThread());
        guardAssignedTo.get(currentWid).release();
    }
}
