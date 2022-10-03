package com.bonitasoft.rest.api;

import groovy.json.JsonBuilder
import groovy.json.JsonOutput

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import org.apache.http.HttpHeaders
import org.bonitasoft.engine.api.IdentityAPI
import org.bonitasoft.web.extension.ResourceProvider
import org.bonitasoft.web.extension.rest.RestApiResponse
import org.bonitasoft.web.extension.rest.RestApiResponseBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.company.example.model.RequestDAO

import org.bonitasoft.web.extension.rest.RestAPIContext
import org.bonitasoft.web.extension.rest.RestApiController

import java.time.LocalDate
import java.time.format.DateTimeFormatter

class Request implements RestApiController {

    private static final Logger LOGGER = LoggerFactory.getLogger(Request)
	// Mapping between the query to invoke and the selected sort column
	private static final Map<String, String> QUERIES = [
		 'persistenceId':'findOrderById',
		 'creationDate':'findOrderByCreationDate',
		 'status':'findOrderByStatusName'
		]

    @Override
    RestApiResponse doHandle(HttpServletRequest request, RestApiResponseBuilder responseBuilder, RestAPIContext context) {
        // Retrieve p parameter, Bonita standard parameter for the pageIndex
        def p = request.getParameter "p"
        if (p == null) {
            return buildResponse(responseBuilder, HttpServletResponse.SC_BAD_REQUEST,"""{"error" : "the parameter p is missing"}""")
        }

        // Retrieve c parameter, Bonita standard parameter for the page size
        def c = request.getParameter "c"
        if (c == null) {
			return buildPagedResponse(responseBuilder, new JsonBuilder([]).toPrettyString(), p.toInteger(), 0, 0 )
        }
		
		// Retrieve o parameter, Bonita standard parameter for the sort criteria
		def o = request.getParameter "o"
		if (o == null) {
			return buildResponse(responseBuilder, HttpServletResponse.SC_BAD_REQUEST,"""{"error" : "the parameter o is missing"}""")
        }
		
		//  Retrieve title parameter, optional custom parameter to filter by title 
		def title = request.getParameter "title"
		if(title != null && title.isBlank()) {
			title = null
		}
		
		//  Retrieve reporterId parameter, optional custom parameter to filter by reporter
		def reporterIdParam = request.getParameter "reporterId"
		def reporterId = reporterIdParam == null || (reporterIdParam != null && reporterIdParam.isBlank()) ? -1 : reporterIdParam.toInteger()
		
		//  Retrieve assigneeId parameter, optional custom parameter to filter by assignee
		def assigneeIdParam = request.getParameter "assigneeId"
		Integer assigneeId = null
		if( assigneeIdParam == null || (assigneeIdParam != null && assigneeIdParam.isBlank())) {
			assigneeId = null
		} else if(assigneeIdParam == 'Any') {
			assigneeId = -1
		} else {
		    assigneeId = assigneeIdParam.toInteger()
		}
		
		def dao = context.apiClient.getDAO(RequestDAO)
		def sortColumn = o.split(' ')[0]
		def sortOrder = o.split(' ')[1].toLowerCase().capitalize()
		if(QUERIES[sortColumn] == null) {
			return buildResponse(responseBuilder, HttpServletResponse.SC_BAD_REQUEST,"""{"error" : "the sort criteria '$sortColumn' is not supported"}""")
		}
		def queryMethod =  QUERIES[sortColumn] + sortOrder
		
		// Use the groovy dynamic method invokation
		LOGGER.info("Invoking RequestDAO.${queryMethod}(title: $title, reporterId: $reporterId, assigneeId: $assigneeId , p: ${p.toInteger() * c.toInteger()}, c: ${c.toInteger()})")
		def identityAPI = context.apiClient.identityAPI
		def result = dao."$queryMethod"(title, reporterId, assigneeId, p.toInteger() * c.toInteger(), c.toInteger()).collect { r -> 
			// Format request data
			[
				persistenceId:r.persistenceId.toString(),
				title:r.title,
				creationDate:r.creationDate.format(DateTimeFormatter.ISO_DATE_TIME),
				reporter:r.reporterId ? identityAPI.getUser(r.reporterId) : null, // Return the whole User object 
				assignee:r.assigneeId ? identityAPI.getUser(r.assigneeId) : null, // Return the whole User object 
				status:r.status
			]
		}
		
        return buildPagedResponse(responseBuilder, new JsonBuilder(result).toPrettyString(), p.toInteger(), c.toInteger(), dao.countRequests(title, reporterId, assigneeId) )
    }

    /**
     * Build an HTTP response.
     *
     * @param  responseBuilder the Rest API response builder
     * @param  httpStatus the status of the response
     * @param  body the response body
     * @return a RestAPIResponse
     */
    RestApiResponse buildResponse(RestApiResponseBuilder responseBuilder, int httpStatus, Serializable body) {
        return responseBuilder.with {
            withResponseStatus(httpStatus)
            withResponse(body)
            build()
        }
    }

    /**
     * Returns a paged result like Bonita BPM REST APIs.
     * Build a response with a content-range.
     *
     * @param  responseBuilder the Rest API response builder
     * @param  body the response body
     * @param  p the page index
     * @param  c the number of result per page
     * @param  total the total number of results
     * @return a RestAPIResponse
     */
    RestApiResponse buildPagedResponse(RestApiResponseBuilder responseBuilder, Serializable body, int p, int c, long total) {
        return responseBuilder.with {
            withContentRange(p,c,total)
            withResponse(body)
            build()
        }
    }


}
