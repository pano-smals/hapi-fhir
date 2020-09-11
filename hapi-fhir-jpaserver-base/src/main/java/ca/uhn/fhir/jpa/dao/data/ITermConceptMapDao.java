package ca.uhn.fhir.jpa.dao.data;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ca.uhn.fhir.jpa.entity.TermConceptMap;

/*
 * #%L
 * HAPI FHIR JPA Server
 * %%
 * Copyright (C) 2014 - 2020 University Health Network
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

public interface ITermConceptMapDao extends JpaRepository<TermConceptMap, Long> {
	@Query("DELETE FROM TermConceptMap cm WHERE cm.myId = :pid")
	@Modifying
	void deleteTermConceptMapById(@Param("pid") Long theId);

	@Query("SELECT cm FROM TermConceptMap cm WHERE cm.myResourcePid = :resource_pid")
	Optional<TermConceptMap> findTermConceptMapByResourcePid(@Param("resource_pid") Long theResourcePid);

	@Query("SELECT cm FROM TermConceptMap cm WHERE cm.myUrl = :url and cm.myVersion is null")
	Optional<TermConceptMap> findTermConceptMapByUrlAndNullVersion(@Param("url") String theUrl);
	
	@Query(value="SELECT cm FROM TermConceptMap cm INNER JOIN ResourceTable r ON r.myId = cm.myResourcePid WHERE cm.myUrl = :url ORDER BY r.myUpdated DESC") 
	List<TermConceptMap> getTermConceptMapEntitiesByUrlOrderByVersion(Pageable thePage, @Param("url") String theUrl);

	@Query("SELECT cm FROM TermConceptMap cm WHERE cm.myUrl = :url AND cm.myVersion = :version")
	Optional<TermConceptMap> findTermConceptMapByUrlAndVersion(@Param("url") String theUrl, @Param("version") String theVersion);
}
