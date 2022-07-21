package org.lareferencia.contrib.ibict.controllers;

import java.util.Optional;
import org.lareferencia.contrib.rcaap.rest.model.representation.Entity;
import org.lareferencia.contrib.rcaap.rest.model.representation.EntityAssembler;
import org.lareferencia.contrib.rcaap.search.services.ISearchService;
import org.lareferencia.core.entity.indexing.service.IEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@RestController(value = "Entities")
@Api(value = "Entities", tags = "APIv2", produces = "application/json", protocols = "https")
@RequestMapping("/entity")
public class EntitiesController {

    @Autowired
    ISearchService searchService;

    @ApiOperation(value = "Returns an entity by uuid", produces = "application/json")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Returns an entity with entity fields containing {expressions}", response = Entity.class) })
    @RequestMapping(value = "/{uuid}", method = RequestMethod.GET)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "uuid", value = "Entity UUID", required = true, dataType = "string", paramType = "path") })
    HttpEntity<Entity> getEntity(@PathVariable("uuid") String uuid) {
        Entity entity;

        Optional<? extends IEntity> resultEntity = searchService.getEntityById(uuid);
        if (resultEntity.isPresent()) {
            entity = new EntityAssembler().toModel(resultEntity.get());
            return new ResponseEntity<>(entity, HttpStatus.OK);
        }

        return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
    }
}
