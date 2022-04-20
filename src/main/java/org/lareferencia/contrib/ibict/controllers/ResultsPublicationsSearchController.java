
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

import java.time.LocalDateTime;

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
import org.lareferencia.core.util.date.DateHelper;
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

@RestController
@Api(value = "Search entities", tags = "APIv2", produces = "application/json", protocols = "https")
// @RequestMapping("${spring.data.rest.base-path}/search")
@RequestMapping("/search")
public class ResultsPublicationsSearchController {
    @Autowired
    ISearchService searchService;

    @Autowired
    private DateHelper dateHelper;

    @Autowired
    @Qualifier("xmlService")
    SearchConfigurationService searchConfigurationService;

    // TODO try to use injection
    ISearchConfigurationContext searchConfigurationContext = new SearchConfigurationContext();

    @ApiOperation(value = "Returns a list of Productions of type Publication", produces = "application/json")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Returns a list of Publications filtered by expressions", response = SearchResult.class) })
    @RequestMapping(value = "/results/publications", method = RequestMethod.GET)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "q", value = "Lucene query syntax - Example: title:\"The Right Way\" AND abstract:go ", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "f", value = "Include facets (true or false)?", required = false, dataType = "boolean", paramType = "query"),
            @ApiImplicitParam(name = "title", value = "Publication title", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "publishedDate", value = "Issue Date - Use YYYY-MM-DD, or YYYY-MM or YYYY", format = "date", allowableValues = "range[1000, 2050]", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "publishedDateStart", value = "Issue Date - Use YYYY-MM-DD, or YYYY-MM or YYYY", format = "date", allowableValues = "range[1000, 2050]", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "publishedDateEnd", value = "Issue Date - Use YYYY-MM-DD, or YYYY-MM or YYYY", format = "date", allowableValues = "range[1000, 2050]", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "abstract", value = "Abstract", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "keyword", value = "Keywords", required = false, allowMultiple = true, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "embargoedDate", value = "Embargoed Date - Use YYYY-MM-DD, or YYYY-MM or YYYY", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "author", value = "Author complex query: \"Carlos, José\" givenName:\"José\" familyName:\"Carlos\" orcid:\"1234-1234-1234-1234\"", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "rightsAccess", value = "COAR Publication access rights vocabulary - use values: open access, embargoed access, restricted access, metadata only access", allowMultiple = true, required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "version", value = "COAR Version Vocabulary - Use the URI or value of any of the version types: http://vocabularies.coar-repositories.org/documentation/version_types/", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "license", value = "License (Creative Commons) - Use the URL or license acronym (CC-BY)", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "identifier", value = "Publication Identifier - A full url of the handle, DOI or URI", allowMultiple = true, required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "type", value = "COAR Types Vocabulary (version 2.0) - Include URI or value of the available options: http://vocabularies.coar-repositories.org/documentation/resource_types/", allowMultiple = true, required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "funding", value = "Funding (complex query) - Example: funderAlternateName:\"FCT\" awardIdentifier:\"12345\"", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "language", value = "Language - Use ISO 639-3, examples: eng, spa, por, …", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "service", value = "Service (complex query) - Example: alternateName:\"ipb\" name:\"Biblioteca Digital do IPB\"", allowMultiple = true, required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "sort", value = "Sorting criteria in the format: property,(asc|desc) "
                    + "Allowed values are: title, date. " + "Default sort order is ascending. "
                    + "Multiple sort criteria aren't supported yet.", required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "size", value = "Number of records per page.", defaultValue = "10", allowableValues = "10,20,100", dataType = "integer", required = false, paramType = "query"),
            @ApiImplicitParam(name = "page", value = "Results page you want to retrieve (0..N)", defaultValue = "0", dataType = "integer", required = false, paramType = "query"), })
    HttpEntity<SearchResult> searchResultsPublications(@RequestParam(name = "q", required = false) String q, boolean f,
            @RequestParam(name = "title", required = false) String title,
            @RequestParam(name = "publishedDate", required = false) String publicationDate,
            @RequestParam(name = "publishedDateStart", required = false) String publicationDateStart,
            @RequestParam(name = "publishedDateEnd", required = false) String publicationDateEnd,
            @RequestParam(name = "abstract", required = false) String description,
            @RequestParam(name = "keyword", required = false) String[] keywords,
            @RequestParam(name = "embargoedDate", required = false) String embargoedDate,
            @RequestParam(name = "author", required = false) String authors,
            @RequestParam(name = "rightsAccess", required = false) String[] access,
            @RequestParam(name = "version", required = false) String version,
            @RequestParam(name = "license", required = false) String license,
            @RequestParam(name = "identifier", required = false) String[] identifiers,
            @RequestParam(name = "type", required = false) String[] types,
            @RequestParam(name = "funding", required = false) String[] funding,
            @RequestParam(name = "language", required = false) String language,
            @RequestParam(name = "service", required = false) String[] services,
            @ApiIgnore("Ignored because swagger ui shows the wrong params, "
                    + "instead they are explained in the implicit params") @NonNull @PageableDefault() Pageable pageable) {

        searchConfigurationContext.setContextConfiguration(
                searchConfigurationService.getSearchConfigurationByName("publicationSearchConfiguration"));

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

                // publicationDateStart
                filtersBuilder.addFilter("title", title).addFilter("abstract", description)
                        .addFilter("keyword", keywords).addFilter("embargoedDate", embargoedDate)
                        // Special type of query, like:
                        // api/search?author=authorFamilyNameFilter:Graca
                        // authorCienciaIdFilter:1234-1234-1234 authorOrcidFilter:5432-1234-1234
                        .addFilter(SearchQueryParser.filterFromQuery(filterService, "author", authors))
                        .addFilter("rightsAccess", access)
                        .addFilter(SearchQueryParser.filterFromQuery(filterService, "identifier", identifiers))
                        .addFilter("version", version).addFilter("license", license).addFilter("type", types)
                        .addFilter(SearchQueryParser.filterFromQuery(filterService, "funding", funding))
                        .addFilter("language", language)
                        .addFilter(SearchQueryParser.filterFromQuery(filterService, "service", services));

                // If we have a single date or an interval/range
                if (publicationDate != null) {
                    LocalDateTime parsedPublicationDate = dateHelper.parseDate(publicationDate);
                    filtersBuilder.addDateRangeFilter("publishedDate", parsedPublicationDate,
                            parsedPublicationDate.plusYears(1));
                } else if (publicationDateStart != null && publicationDateEnd != null) {
                    filtersBuilder.addDateRangeFilter("publishedDate", dateHelper.parseDate(publicationDateStart),
                            dateHelper.parseDate(publicationDateEnd));
                }
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

            Link selfLink = linkTo(
                    methodOn(this.getClass()).searchResultsPublications(QueryStringParserHelper.encode(q), f,
                            QueryStringParserHelper.encode(title), QueryStringParserHelper.encode(publicationDate),
                            QueryStringParserHelper.encode(publicationDateStart),
                            QueryStringParserHelper.encode(publicationDateEnd),
                            QueryStringParserHelper.encode(description), QueryStringParserHelper.encode(keywords),
                            QueryStringParserHelper.encode(embargoedDate), QueryStringParserHelper.encode(authors),
                            QueryStringParserHelper.encode(access), QueryStringParserHelper.encode(version),
                            QueryStringParserHelper.encode(license), QueryStringParserHelper.encode(identifiers),
                            QueryStringParserHelper.encode(types), QueryStringParserHelper.encode(funding),
                            QueryStringParserHelper.encode(language), services, pageable)).withSelfRel();

            entities.add(selfLink);

            return new ResponseEntity<>(entities, HttpStatus.OK);

        } catch (FilterNotFoundException e) {
            e.printStackTrace();
        } catch (FilterLoopException e1) {
            e1.printStackTrace();
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        return null;

    }

}
// @ApiParam(value = "description for query-parameter") @QueryParam("username")