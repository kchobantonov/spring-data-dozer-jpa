package org.springframework.data.dozer.jpa.repository.support;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.data.dozer.repository.support.DozerEntityInformation;
import org.springframework.data.dozer.repository.support.SimpleDozerRepository;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.transaction.annotation.Transactional;

import com.github.dozermapper.core.Mapper;

@Transactional
public class SimpleDozerJpaRepository<T, ID> extends SimpleDozerRepository<T, ID> {

	public SimpleDozerJpaRepository(RepositoryInformation repositoryInformation,
			DozerEntityInformation<T, ?> entityInformation, Mapper dozerMapper, String conversionServiceName,
			BeanFactory beanFactory) {
		super(repositoryInformation, entityInformation, dozerMapper, conversionServiceName, beanFactory);
	}

}