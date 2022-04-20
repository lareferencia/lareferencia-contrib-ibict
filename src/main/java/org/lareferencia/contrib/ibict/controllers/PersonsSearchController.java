
/*
 *   Copyright (c) 2013-2022. LA Referencia / Red CLARA and others
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *   This file is part of LA Referencia software platform LRHarvester v4.x
 *   For any further information please contact Lautaro Matas <lmatas@gmail.com>
 */

package org.lareferencia.contrib.ibict.controllers;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import org.lareferencia.contrib.rcaap.rest.helper.QueryStringParserHelper;
import org.lareferencia.contrib.rcaap.rest.model.representation.SearchResult;
import org.lareferencia.contrib.rcaap.rest.model.representation.SearchResultAssembler;
import org.lareferencia.contrib.rcaap.rest.model.representation.SearchResultFacetedAssembler;
import org.lareferencia.contrib.rcaap.search.extended.model.FiltersBuilder;
import org.lareferencia.contrib.rcaap.search.queryparser.SearchQueryParser;
import org.lareferencia.contrib.rcaap.search.services.ISearchConfigurationContext;
import org.lareferencia.contrib.rcaap.search.services.ISearchService;
import org.lareferencia.contrib.rcaap.search.services.PageableSearchBuilder;
import org.lareferencia.contrib.rcaap.search.services.SearchConfigurationContext;
import org.lareferencia.contrib.rcaap.search.services.SearchConfigurationService;
import org.lareferencia.contrib.rcaap.search.services.model.FilterLoopException;
import org.lareferencia.contrib.rcaap.search.services.model.FilterNotFoundException;
import org.lareferencia.contrib.rcaap.search.services.model.SearchFilterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.hateoas.Link;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import springfox.documentation.annotations.ApiIgnore;

@RestController(value = "Search entities")
@Api(value = "Search entities", tags = "APIv2", produces = "application/json", protocols = "https")
@RequestMapping("/search")
public class PersonsSearchController {

    @Autowired
    ISearchService searchService;

    @Autowired
    @Qualifier("xmlService")
    SearchConfigurationService searchConfigurationService;

    // TODO try to use injection
    ISearchConfigurationContext searchConfigurationContext = new SearchConfigurationContext();

    @ApiOperation(value = "Returns a list of Persons filtered by expressions", produces = "application/json")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Returns a list of entities for a certain {type} with entity fields containing {expressions}", response = SearchResult.class) })
    @RequestMapping(value = "/persons", method = RequestMethod.GET)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "q", value = "Lucene query syntax - Example: orcid:\"0000-0001-5804-2982\" AND cienciaID:\"D41F-DF04-7EE8\" ", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "f", value = "Include facets (true or false)?", required = false, dataType = "boolean", paramType = "query"),
            @ApiImplicitParam(name = "orcid", value = "Author's ORCID Identifier - Example: 0000-0001-5804-2982", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "cienciaID", value = "Author's CienciaID Identifier - Example: D41F-DF04-7EE8", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "name", value = "Person's name complex query: \\\"José Carlos\\\" \\n givenName:\\\"José\\\" familyName:\\\"Carlos\\\"", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "affiliation", value = "Person's affiliation complex query: \\\"Universidade de Lisboa\\\" \\n isni:\\\"1234-1234-1234-1234\\\" \\n ringgold:\\\"123456\\\"", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "sort", value = "Sorting criteria in the format: property,(asc|desc) "
                    + "Allowed values are: name, givenName, familyName. " + "Default sort order is ascending. "
                    + "Multiple sort criteria are supported.", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "size", value = "Number of records per page.", defaultValue = "10", allowableValues = "10,20,100", dataType = "integer", required = false, paramType = "query"),
            @ApiImplicitParam(name = "page", value = "Results page you want to retrieve (0..N)", defaultValue = "0", dataType = "integer", required = false, paramType = "query"),

    })
    HttpEntity<SearchResult> searchPersons(@RequestParam(name = "q", required = false) String q, boolean f,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "orcid", required = false) String orcid,
            @RequestParam(name = "cienciaID", required = false) String cienciaID,
            @RequestParam(name = "affiliation", required = false) String affiliation,
            @ApiIgnore("Ignored because swagger ui shows the wrong params, "
                    + "instead they are explained in the implicit params") @NonNull @PageableDefault() Pageable pageable) {

        searchConfigurationContext.setContextConfiguration(
                searchConfigurationService.getSearchConfigurationByName("personSearchConfiguration"));

        SearchFilterService filterService = searchConfigurationContext.getSearchFilterService();

        PageableSearchBuilder pageableSearchBuilder = new PageableSearchBuilder(searchConfigurationContext)
                .setFromPageable(pageable);

        try {

            FiltersBuilder filtersBuilder = new FiltersBuilder(filterService);
            // check:
            // https://lucene.apache.org/core/8_6_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html
            // for query syntax
            if (q != null) {

                filtersBuilder.addFilter(SearchQueryParser.filterFromQuery(filterService, q));

            } else {

                filtersBuilder.addFilter("orcid", orcid).addFilter("cienciaID", cienciaID)
                        .addFilter(SearchQueryParser.filterFromQuery(filterService, "name", name))
                        .addFilter(SearchQueryParser.filterFromQuery(filterService, "affiliation", affiliation));
            }

            SearchResult entities = null;
            // If facet is true and if there are configured facets
            if (f && searchConfigurationContext.getSearchFacetService().hasFacets()) {
                entities = new SearchResultFacetedAssembler()
                        .toModel(searchService.searchEntitiesWFacetsBySearchConfiguration(searchConfigurationContext,
                                filtersBuilder, pageableSearchBuilder));

            } else {
                entities = new SearchResultAssembler().toModel(searchService.searchEntitiesBySearchConfiguration(
                        searchConfigurationContext, filtersBuilder, pageableSearchBuilder));

            }

            Link selfLink = linkTo(methodOn(this.getClass()).searchPersons(QueryStringParserHelper.encode(q), f,
                    QueryStringParserHelper.encode(name), QueryStringParserHelper.encode(orcid),
                    QueryStringParserHelper.encode(cienciaID), QueryStringParserHelper.encode(affiliation), pageable))
                            .withSelfRel();

            entities.add(selfLink);

            return new ResponseEntity<>(entities, HttpStatus.OK);
        } catch (FilterNotFoundException e) {
            e.printStackTrace();
            // TODO throw error
        } catch (FilterLoopException e1) {
            e1.printStackTrace();
            // TODO throw error
        }
        return null;
    }

}
