package org.springframework.data.dozer.jpa.repository.support;

import java.util.Optional;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

	@Override
	public Iterable<T> findAll(Sort sort) {
		return super.findAll(sort);
	}

	@Override
	public Page<T> findAll(Pageable pageable) {
		return super.findAll(pageable);
	}

	@Override
	public <S extends T> S save(S resource) {
		return super.save(resource);
	}

	@Override
	public <S extends T> Iterable<S> saveAll(Iterable<S> resources) {
		return super.saveAll(resources);
	}

	@Override
	public Optional<T> findById(ID resourceId) {
		return super.findById(resourceId);
	}

	@Override
	public boolean existsById(ID resourceId) {
		return super.existsById(resourceId);
	}

	@Override
	public Iterable<T> findAll() {
		return super.findAll();
	}

	@Override
	public Iterable<T> findAllById(Iterable<ID> resourceIds) {
		return super.findAllById(resourceIds);
	}

	@Override
	public long count() {
		return super.count();
	}

	@Override
	public void deleteById(ID resourceId) {
		super.deleteById(resourceId);
	}

	@Override
	public void delete(T resource) {
		super.delete(resource);
	}

	@Override
	public void deleteAll(Iterable<? extends T> resources) {
		super.deleteAll(resources);
	}

	@Override
	public void deleteAll() {
		super.deleteAll();
	}

}
