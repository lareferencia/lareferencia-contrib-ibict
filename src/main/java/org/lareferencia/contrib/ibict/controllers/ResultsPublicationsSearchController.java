package org.lareferencia.contrib.ibict.controllers;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import org.lareferencia.contrib.rcaap.rest.helper.QueryStringParserHelper;
import org.lareferencia.contrib.rcaap.rest.model.representation.SearchResult;
import org.lareferencia.contrib.rcaap.rest.model.representation.SearchResultAssembler;
import org.lareferencia.contrib.rcaap.search.extended.model.FiltersBuilder;
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

@RestController
@Api(value = "Search entities", tags = "APIv2", produces = "application/json", protocols = "https")
@RequestMapping("/search")
public class ResultsPublicationsSearchController {
	@Autowired
	ISearchService searchService;

	@Autowired
	@Qualifier("xmlService")
	SearchConfigurationService searchConfigurationService;

	// TODO try to use injection
	ISearchConfigurationContext searchConfigurationContext = new SearchConfigurationContext();

	@ApiOperation(value = "Returns a list of entities of type Publication", produces = "application/json")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Returns a list of Publications filtered by expressions", response = SearchResult.class) })
	@RequestMapping(value = "/results/publications", method = RequestMethod.GET)
	@ApiImplicitParams({
		@ApiImplicitParam(name = "title", value = "Publication title - Use normalized title: lower case, no stopwords, no accents, no punctuation", required = false, dataType = "string", paramType = "query"),
		@ApiImplicitParam(name = "publicationDate", value = "Publication Date - Use YYYY", allowableValues = "range[1000, 2050]", required = false, dataType = "string", paramType = "query"),
		@ApiImplicitParam(name = "personId", value = "Author, advisor, or co-advisor Lattes ID or Orcid", required = true, dataType = "string", paramType = "query"),
		@ApiImplicitParam(name = "type", value = "Publication type, e.g master thesis, doctoral thesis, conference proceedings, etc.", required = false, dataType = "string", paramType = "query"),
		@ApiImplicitParam(name = "communityId", value = "BrCris internal Community ID", required = false, dataType = "string", paramType = "query"),
		@ApiImplicitParam(name = "size", value = "Number of records per page.", defaultValue = "10", allowableValues = "10,20,100", dataType = "integer", required = false, paramType = "query"),
		@ApiImplicitParam(name = "page", value = "Results page you want to retrieve (0..N)", defaultValue = "0", dataType = "integer", required = false, paramType = "query"), })
	HttpEntity<SearchResult> searchResultsPublications(@RequestParam(name = "title", required = false) String title,
			@RequestParam(name = "publicationDate", required = false) String publicationDate,
			@RequestParam(name = "personId", required = true) String personId,
			@RequestParam(name = "type", required = false) String type,
			@RequestParam(name = "communityId", required = false) String communityId,
			@ApiIgnore("Ignored because swagger ui shows the wrong params, "
					+ "instead they are explained in the implicit params") @NonNull @PageableDefault() Pageable pageable) {

		searchConfigurationContext.setContextConfiguration(
				searchConfigurationService.getSearchConfigurationByName("publicationSearchConfiguration"));

		SearchFilterService filterService = searchConfigurationContext.getSearchFilterService();
		PageableSearchBuilder pageableSearchBuilder = new PageableSearchBuilder(searchConfigurationContext)
				.setFromPageable(pageable);

		try {
			FiltersBuilder filtersBuilder = new FiltersBuilder(filterService);

			filtersBuilder
				.addFilter("title", title)
				.addFilter("publicationDate", publicationDate)
				.addFilter("personId", personId)
				.addFilter("type", type)
				.addFilter("communityId", communityId);

			SearchResult entities = null;
			entities = new SearchResultAssembler().toModel(searchService.searchEntitiesBySearchConfiguration(
					searchConfigurationContext, filtersBuilder, pageableSearchBuilder));

			Link selfLink = linkTo(
					methodOn(this.getClass()).searchResultsPublications(
							QueryStringParserHelper.encode(title), 
							QueryStringParserHelper.encode(publicationDate),
							QueryStringParserHelper.encode(personId),
							QueryStringParserHelper.encode(type), 
							QueryStringParserHelper.encode(communityId), 
							pageable)).withSelfRel();

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
