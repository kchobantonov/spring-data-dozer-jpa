package org.springframework.data.dozer.jpa.repository.query;

import java.lang.reflect.Method;
import java.util.Set;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.dozer.annotation.DozerEntity;
import org.springframework.data.dozer.annotation.DozerRepository;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.CrudMethods;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.AbstractRepositoryMetadata;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.RepositoryQuery;

import com.github.dozermapper.core.Mapper;

public class DozerQueryLookupStrategy implements QueryLookupStrategy {

	private final Mapper dozerMapper;
	private final QueryLookupStrategy adaptedQueryLookupStrategy;
	private final String conversionServiceName;
	private final BeanFactory beanFactory;

	public DozerQueryLookupStrategy(Mapper dozerMapper, String conversionServiceName, BeanFactory beanFactory,
			QueryLookupStrategy adaptedQueryLookupStrategy) {
		this.dozerMapper = dozerMapper;
		this.adaptedQueryLookupStrategy = adaptedQueryLookupStrategy;
		this.conversionServiceName = conversionServiceName;
		this.beanFactory = beanFactory;
	}

	@Override
	public RepositoryQuery resolveQuery(Method method, RepositoryMetadata metadata, ProjectionFactory factory,
			NamedQueries namedQueries) {

		DozerRepository dozerRepository = AnnotatedElementUtils.findMergedAnnotation(metadata.getRepositoryInterface(),
				DozerRepository.class);
		if (dozerRepository != null) {

			RepositoryMetadata adaptedMetadata = AbstractRepositoryMetadata
					.getMetadata(dozerRepository.adaptedRepositoryClass());

			return new DozerRepositoryQuery(new DozerQueryMethod(method, metadata, factory), dozerMapper,
					adaptedQueryLookupStrategy.resolveQuery(method, adaptedMetadata, factory, namedQueries),
					conversionServiceName, beanFactory);
		}

		return new DozerRepositoryQuery(
				new DozerQueryMethod(method, metadata, factory), dozerMapper, adaptedQueryLookupStrategy
						.resolveQuery(method, new AdaptedRepositoryMetadata(metadata), factory, namedQueries),
				conversionServiceName, beanFactory);

	}

	private class AdaptedRepositoryMetadata implements RepositoryMetadata {
		private RepositoryMetadata delegate;

		public AdaptedRepositoryMetadata(RepositoryMetadata delegate) {
			this.delegate = delegate;
		}

		/**
		 * @return
		 * @see org.springframework.data.repository.core.RepositoryMetadata#getIdType()
		 */
		public Class<?> getIdType() {
			return delegate.getIdType();
		}

		/**
		 * @return
		 * @see org.springframework.data.repository.core.RepositoryMetadata#getDomainType()
		 */
		public Class<?> getDomainType() {
			Class<?> domainType = delegate.getDomainType();

			DozerEntity dozerEntity = AnnotatedElementUtils.findMergedAnnotation(domainType, DozerEntity.class);
			if (dozerEntity != null) {
				return dozerEntity.adaptedDomainClass();
			}
			return domainType;
		}

		/**
		 * @return
		 * @see org.springframework.data.repository.core.RepositoryMetadata#getRepositoryInterface()
		 */
		public Class<?> getRepositoryInterface() {
			Class<?> repositoryInterface = delegate.getRepositoryInterface();

			DozerRepository dozerRepository = AnnotatedElementUtils.findMergedAnnotation(repositoryInterface,
					DozerRepository.class);
			if (dozerRepository != null) {
				return dozerRepository.adaptedRepositoryClass();
			}

			return repositoryInterface;
		}

		/**
		 * @param method
		 * @return
		 * @see org.springframework.data.repository.core.RepositoryMetadata#getReturnedDomainClass(java.lang.reflect.Method)
		 */
		public Class<?> getReturnedDomainClass(Method method) {
			Class<?> returnedDomainClass = delegate.getReturnedDomainClass(method);
			DozerEntity dozerEntity = AnnotatedElementUtils.findMergedAnnotation(returnedDomainClass,
					DozerEntity.class);
			if (dozerEntity != null) {
				return dozerEntity.adaptedDomainClass();
			}
			return returnedDomainClass;
		}

		/**
		 * @return
		 * @see org.springframework.data.repository.core.RepositoryMetadata#getCrudMethods()
		 */
		public CrudMethods getCrudMethods() {
			return delegate.getCrudMethods();
		}

		/**
		 * @return
		 * @see org.springframework.data.repository.core.RepositoryMetadata#isPagingRepository()
		 */
		public boolean isPagingRepository() {
			return delegate.isPagingRepository();
		}

		/**
		 * @return
		 * @see org.springframework.data.repository.core.RepositoryMetadata#getAlternativeDomainTypes()
		 */
		public Set<Class<?>> getAlternativeDomainTypes() {
			return delegate.getAlternativeDomainTypes();
		}

		/**
		 * @return
		 * @see org.springframework.data.repository.core.RepositoryMetadata#isReactiveRepository()
		 */
		public boolean isReactiveRepository() {
			return delegate.isReactiveRepository();
		}

	}
}
