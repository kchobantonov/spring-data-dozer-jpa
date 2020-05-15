package org.springframework.data.dozer.jpa.repository.query;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.stream.Stream;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.CollectionFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.Slice;
import org.springframework.data.dozer.repository.query.DozerEntityMetadata;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.util.Lazy;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import com.github.dozermapper.core.Mapper;
import com.github.dozermapper.core.classmap.ClassMap;
import com.github.dozermapper.core.classmap.ClassMappings;
import com.github.dozermapper.core.classmap.Configuration;
import com.github.dozermapper.core.classmap.MappingDirection;
import com.github.dozermapper.core.metadata.MetadataLookupException;

public class DozerRepositoryQuery implements RepositoryQuery {
	private final DozerQueryMethod method;
	private final Mapper dozerMapper;
	private final RepositoryQuery resolveQuery;
	private final Lazy<ConversionService> conversionService;

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
		Object result = resolveQuery.execute(parameters);

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

		if (!hasDozerMapping(entityInformation.getJavaType(), entityInformation.getAdaptedJavaType(),
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

	protected boolean hasDozerMapping(Class<?> srcClass, Class<?> destClass, String mapId) {
		ClassMap classMap = getClassMap(srcClass, destClass, mapId);

		if (classMap != null) {
			return true;
		}

		Configuration configuration = getGlobalConfiguration();

		if (configuration.getCustomConverters() != null
				&& configuration.getCustomConverters().findConverter(srcClass, destClass) != null) {
			return true;
		}

		return false;
	}

	protected ClassMap getClassMap(Class<?> srcClass, Class<?> destClass, String mapId) {
		ClassMappings classMappings = getClassMappings();

		ClassMap mapping = classMappings.find(srcClass, destClass, mapId);

		if (mapping == null) {
			mapping = classMappings.find(destClass, srcClass, mapId);
			if (mapping != null && MappingDirection.ONE_WAY == mapping.getType()) {
				return null;
			} else {
				return null;
			}
		}

		return mapping;
	}

	protected Configuration getGlobalConfiguration() {
		Field globalConfigurationField = ReflectionUtils.findField(dozerMapper.getMapperModelContext().getClass(),
				"globalConfiguration", Configuration.class);
		ReflectionUtils.makeAccessible(globalConfigurationField);
		Configuration globalConfiguration = (Configuration) ReflectionUtils.getField(globalConfigurationField,
				dozerMapper.getMapperModelContext());
		return globalConfiguration;
	}

	protected ClassMappings getClassMappings() {
		Field classMappingsField = ReflectionUtils.findField(dozerMapper.getMappingMetadata().getClass(),
				"classMappings", ClassMappings.class);
		ReflectionUtils.makeAccessible(classMappingsField);
		ClassMappings classMappings = (ClassMappings) ReflectionUtils.getField(classMappingsField,
				dozerMapper.getMappingMetadata());
		return classMappings;
	}

}
