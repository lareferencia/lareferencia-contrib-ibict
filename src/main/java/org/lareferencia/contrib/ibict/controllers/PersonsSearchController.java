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
            @ApiResponse(code = 200, message = "Returns a list of Persons filtered by expressions", response = SearchResult.class) })
    @RequestMapping(value = "/persons", method = RequestMethod.GET)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "q", value = "Lucene query syntax - Example: orcid:\"0000-0001-5804-2982\" AND cienciaID:\"D41F-DF04-7EE8\" ", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "f", value = "Include facets (true or false)?", required = false, dataType = "boolean", paramType = "query"),
            @ApiImplicitParam(name = "identifier", value = "Person's Lattes identifier, ORCID or CPF", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "name", value = "Person's name or citation name", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "email", value = "Person's email address", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "nationality", value = "Person's nationality", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "affiliation", value = "Person's affiliation name or acronym", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "knowledgeArea", value = "Person's research areas", required = false, allowMultiple = true, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "community", value = "The name of the community the person belongs to", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "sort", value = "Sorting criteria in the format: property,(asc|desc) "
            		 + "Allowed values are: name, citationName. " + "Default sort order is ascending. "
                     + "Multiple sort criteria are supported.", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "size", value = "Number of records per page.", defaultValue = "10", allowableValues = "10,20,100", dataType = "integer", required = false, paramType = "query"),
            @ApiImplicitParam(name = "page", value = "Results page you want to retrieve (0..N)", defaultValue = "0", dataType = "integer", required = false, paramType = "query"),

    })
    HttpEntity<SearchResult> searchPersons(@RequestParam(name = "q", required = false) String q, boolean f,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "identifier", required = false) String identifier,
            @RequestParam(name = "email", required = false) String email,
            @RequestParam(name = "nationality", required = false) String nationality,
            @RequestParam(name = "affiliation", required = false) String affiliation,
            @RequestParam(name = "knowledgeArea", required = false) String[] knowledgeAreas,
            @RequestParam(name = "community", required = false) String community,
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

                filtersBuilder.addFilter("identifier", identifier).addFilter("name", name)
                        .addFilter("email", email).addFilter("nationality", nationality)
                        .addFilter("affiliation", affiliation).addFilter("community", community)
                        .addFilter("knowledgeArea", knowledgeAreas);
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
                    QueryStringParserHelper.encode(name), QueryStringParserHelper.encode(identifier),
                    QueryStringParserHelper.encode(email), QueryStringParserHelper.encode(nationality),
                    QueryStringParserHelper.encode(affiliation), QueryStringParserHelper.encode(knowledgeAreas), 
                    QueryStringParserHelper.encode(community), pageable)).withSelfRel();

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
