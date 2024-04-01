/*
 * University of Warsaw
 * Concurrent Programming Course 2022/2023
 * Java Assignment
 *
 * Author: Konrad Iwanicki (iwanicki@mimuw.edu.pl)
 */
package cp2022.solution;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import cp2022.base.Workplace;
import cp2022.base.WorkplaceId;
import cp2022.base.Workshop;


public final class WorkshopFactory {

    public final static Workshop newWorkshop(
            Collection<Workplace> workplaces
    ) {
        ConcurrentHashMap<WorkplaceId, Semaphore> guardAssignedTo = new ConcurrentHashMap<>();
        ConcurrentHashMap<Thread, WorkplaceId> workplaceOccupiedBy = new ConcurrentHashMap<>();
        ConcurrentHashMap<WorkplaceId, Workplace> idToWorkplace = new ConcurrentHashMap<>();
        for (Workplace workplace : workplaces) {
            WorkplaceId wid = workplace.getId();
            guardAssignedTo.put(wid, new Semaphore(1, true));
            idToWorkplace.put(wid, workplace);
        }
        return new SharedWorkshop(guardAssignedTo, workplaceOccupiedBy, idToWorkplace);
    }
    
}
