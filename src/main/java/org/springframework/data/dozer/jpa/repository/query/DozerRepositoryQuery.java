package org.springframework.data.dozer.jpa.repository.query;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.CollectionFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.dozer.repository.query.DozerEntityMetadata;
import org.springframework.data.dozer.repository.support.DozerUtil;
import org.springframework.data.dozer.repository.support.DozerUtilFactory;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.util.Lazy;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.github.dozermapper.core.Mapper;
import com.github.dozermapper.core.metadata.MetadataLookupException;

public class DozerRepositoryQuery implements RepositoryQuery {
	private final DozerQueryMethod method;
	private final Mapper dozerMapper;
	private final RepositoryQuery resolveQuery;
	private final Lazy<ConversionService> conversionService;
	protected Map<String, String> dozerEntityFieldNameToAdaptedFieldName;
	protected boolean dozerEntityFieldNameToAdaptedFieldNameInitialized = false;

	public DozerRepositoryQuery(DozerQueryMethod method, Mapper dozerMapper, RepositoryQuery resolveQuery,
			String conversionServiceName, final BeanFactory beanFactory) {
		Assert.isInstanceOf(ListableBeanFactory.class, beanFactory, "beanFactory must be of type ListableBeanFactory!");

		this.method = method;
		this.dozerMapper = dozerMapper;
		this.resolveQuery = resolveQuery;

		this.conversionService = Lazy.of(() -> ((ListableBeanFactory) beanFactory)
				.getBeansOfType(ConversionService.class).get(conversionServiceName));
	}

	@Override
	public Object execute(Object[] parameters) {
		Object result = resolveQuery.execute(toAdaptedParameters(parameters));

		if (result == null || method.getResultProcessor().getReturnedType().isProjecting()) {
			return result;
		}

		if (method.isModifyingQuery()) {
			return result;
		}

		if (result instanceof Slice && method.isPageQuery() || method.isSliceQuery()) {
			return ((Slice<?>) result).map(source -> toDozerEntity(source));
		}

		if (method.isQueryForEntity() && method.getEntityInformation().getAdaptedJavaType().isInstance(result)) {
			return toDozerEntity(result);
		}

		if (result instanceof Collection && method.isCollectionQuery()) {
			Collection<?> collection = (Collection<?>) result;
			Collection<Object> target = createCollectionFor(collection);

			for (Object columns : collection) {
				target.add(
						method.getEntityInformation().getAdaptedJavaType().isInstance(columns) ? toDozerEntity(columns)
								: columns);
			}

			return target;
		}

		if (result instanceof Stream && method.isStreamQuery()) {
			return ((Stream<Object>) result)
					.map(t -> method.getEntityInformation().getAdaptedJavaType().isInstance(t) ? t : toDozerEntity(t));
		}

		return result;
	}

	protected Object[] toAdaptedParameters(Object[] parameters) {
		if (!dozerEntityFieldNameToAdaptedFieldNameInitialized) {
			synchronized (this) {
				if (!dozerEntityFieldNameToAdaptedFieldNameInitialized) {
					DozerUtil dozerUtil = DozerUtilFactory.getInstance().getDozerUtil(dozerMapper);

					dozerEntityFieldNameToAdaptedFieldName = dozerUtil
							.getDozerEntityFieldNameToAdaptedFieldNameMap(method.getEntityInformation());
					dozerEntityFieldNameToAdaptedFieldNameInitialized = true;
				}
			}
		}

		if (hasSortParameter(parameters) && dozerEntityFieldNameToAdaptedFieldName != null
				&& !dozerEntityFieldNameToAdaptedFieldName.isEmpty()) {

			Object[] newParameters = new Object[parameters.length];
			System.arraycopy(parameters, 0, newParameters, 0, parameters.length);

			for (int i = 0; i < newParameters.length; i++) {
				Object parameter = newParameters[i];
				if (parameter instanceof Sort && ((Sort) parameter).isSorted()) {
					Sort sort = toAdaptedSort((Sort) parameter);
					newParameters[i] = sort;
				} else if (parameter instanceof Pageable && ((Pageable) parameter).getSort().isSorted()) {
					Pageable pageable = toAdaptedPageable((Pageable) parameter);
					newParameters[i] = pageable;
				}
			}

			return newParameters;
		}

		return parameters;
	}

	private boolean hasSortParameter(Object[] parameters) {
		if (parameters != null) {
			for (Object parameter : parameters) {
				if (parameter instanceof Sort && ((Sort) parameter).isSorted()) {
					return true;
				} else if (parameter instanceof Pageable && ((Pageable) parameter).getSort().isSorted()) {
					return true;
				}
			}

		}
		return false;
	}

	@Override
	public QueryMethod getQueryMethod() {
		return resolveQuery.getQueryMethod();
	}

	protected Object toDozerEntity(Object source) {
		if (useConversionServiceForEntityMapping(method.getEntityInformation())) {
			return conversionService.getOptional().get().convert(source, method.getEntityInformation().getJavaType());
		}

		if (StringUtils.isEmpty(method.getEntityInformation().getDozerMapId())) {
			return dozerMapper.map(source, method.getEntityInformation().getJavaType());
		}
		return dozerMapper.map(source, method.getEntityInformation().getJavaType(),
				method.getEntityInformation().getDozerMapId());
	}

	protected boolean useConversionServiceForEntityMapping(DozerEntityMetadata entityInformation) {
		boolean considerConversionServiceForEntityMapping = entityInformation.getMapEntityUsingConvertionService()
				&& conversionService.getOptional().isPresent();

		DozerUtil dozerUtil = DozerUtilFactory.getInstance().getDozerUtil(dozerMapper);

		if (!dozerUtil.hasDozerMapping(entityInformation.getJavaType(), entityInformation.getAdaptedJavaType(),
				entityInformation.getDozerMapId())) {
			if (!considerConversionServiceForEntityMapping || !conversionService.getOptional().get()
					.canConvert(entityInformation.getAdaptedJavaType(), entityInformation.getJavaType())) {
				throw new MetadataLookupException(
						"No mapping definition found for: " + entityInformation.getJavaType().getName() + " -> "
								+ entityInformation.getAdaptedJavaType().getName() + ".");
			}
			return true;
		}

		return false;
	}

	/**
	 * Creates a new {@link Collection} for the given source. Will try to create an
	 * instance of the source collection's type first falling back to creating an
	 * approximate collection if the former fails.
	 *
	 * @param source must not be {@literal null}.
	 * @return
	 */
	private static Collection<Object> createCollectionFor(Collection<?> source) {

		try {
			return CollectionFactory.createCollection(source.getClass(), source.size());
		} catch (RuntimeException o_O) {
			return CollectionFactory.createApproximateCollection(source, source.size());
		}
	}

	protected Sort toAdaptedSort(Sort sort) {
		if (sort.isSorted() && dozerEntityFieldNameToAdaptedFieldName != null
				&& !dozerEntityFieldNameToAdaptedFieldName.isEmpty()) {
			sort = Sort.by(sort.toList().stream().map(it -> toAdaptedOrder(it)).collect(Collectors.toList()));
		}

		return sort;
	}

	protected Order toAdaptedOrder(Order order) {
		if (dozerEntityFieldNameToAdaptedFieldName != null && !dozerEntityFieldNameToAdaptedFieldName.isEmpty()) {
			return order.withProperty(
					dozerEntityFieldNameToAdaptedFieldName.getOrDefault(order.getProperty(), order.getProperty()));
		}

		return order;
	}

	protected Pageable toAdaptedPageable(Pageable pageable) {
		if (pageable.getSort().isSorted() && dozerEntityFieldNameToAdaptedFieldName != null
				&& !dozerEntityFieldNameToAdaptedFieldName.isEmpty()) {
			pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
					toAdaptedSort(pageable.getSort()));
		}

		return pageable;
	}
}
