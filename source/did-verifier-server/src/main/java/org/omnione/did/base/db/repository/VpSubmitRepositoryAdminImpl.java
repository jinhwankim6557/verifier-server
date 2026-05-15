package org.omnione.did.base.db.repository;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import org.omnione.did.base.db.constant.TransactionStatus;
import org.omnione.did.base.db.domain.QTransaction;
import org.omnione.did.base.db.domain.QVpSubmit;
import org.omnione.did.base.db.domain.VpSubmit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin VP History 조회 Repository.
 *
 * 설계 원칙: VpSubmit이 "제출 시도 증적의 단일 진실 원천". 여기서는 VpSubmit
 * 테이블을 주 엔티티로 SELECT 하고, Transaction은 상태/txId 표시용으로 JOIN 한다.
 * - 제출 시도가 일어나지 않은 Transaction을 placeholder로 만들어 넣지 않는다.
 * - Transaction.type 필터(VP_SUBMIT만)를 쓰지 않아 프로토콜(DID VP / OID4VP)에
 *   중립이다.
 */
@Repository
@RequiredArgsConstructor
public class VpSubmitRepositoryAdminImpl implements VpSubmitRepositoryAdmin {
    private final JPAQueryFactory queryFactory;

    @Override
    public Page<VpSubmit> searchVpSubmitList(String searchKey, String searchValue, Pageable pageable) {
        QVpSubmit qVpSubmit = QVpSubmit.vpSubmit;
        QTransaction qTransaction = QTransaction.transaction;

        BooleanExpression predicate = buildPredicate(searchKey, searchValue, qVpSubmit, qTransaction);

        long total = queryFactory
                .select(qVpSubmit.count())
                .from(qVpSubmit)
                .join(qTransaction).on(qVpSubmit.transactionId.eq(qTransaction.id))
                .where(predicate)
                .fetchOne();

        List<VpSubmit> results = queryFactory
                .selectFrom(qVpSubmit)
                .join(qTransaction).on(qVpSubmit.transactionId.eq(qTransaction.id))
                .where(predicate)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(getOrderSpecifier(pageable, qVpSubmit, qTransaction))
                .fetch();

        return new PageImpl<>(results, pageable, total);
    }

    private BooleanExpression buildPredicate(String searchKey, String searchValue,
                                             QVpSubmit qVpSubmit, QTransaction qTransaction) {
        if (searchKey == null || searchValue == null || searchValue.isEmpty()) {
            return null;
        }

        return switch (searchKey) {
            case "transaction" -> qVpSubmit.transactionId.eq(Long.valueOf(searchValue));
            case "status" -> qTransaction.status.eq(TransactionStatus.valueOf(searchValue));
            default -> null;
        };
    }

    private OrderSpecifier<?>[] getOrderSpecifier(Pageable pageable, QVpSubmit qVpSubmit, QTransaction qTransaction) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();

        if (!pageable.getSort().isSorted()) {
            orders.add(new OrderSpecifier<>(Order.DESC, qVpSubmit.createdAt));
        } else {
            for (Sort.Order order : pageable.getSort()) {
                Order direction = order.isAscending() ? Order.ASC : Order.DESC;
                switch (order.getProperty()) {
                    case "createdAt" -> orders.add(new OrderSpecifier<>(direction, qVpSubmit.createdAt));
                    case "transactionStatus" -> orders.add(new OrderSpecifier<>(direction, qTransaction.status));
                    default -> orders.add(new OrderSpecifier<>(direction, qVpSubmit.createdAt));
                }
            }
        }

        if (orders.isEmpty()) {
            orders.add(new OrderSpecifier<>(Order.DESC, qVpSubmit.createdAt));
        }

        return orders.toArray(new OrderSpecifier[0]);
    }
}
