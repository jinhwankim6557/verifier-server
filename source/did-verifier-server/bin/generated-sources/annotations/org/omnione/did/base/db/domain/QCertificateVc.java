package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QCertificateVc is a Querydsl query type for CertificateVc
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QCertificateVc extends EntityPathBase<CertificateVc> {

    private static final long serialVersionUID = 1951720231L;

    public static final QCertificateVc certificateVc = new QCertificateVc("certificateVc");

    public final QBaseEntity _super = new QBaseEntity(this);

    //inherited
    public final DateTimePath<java.time.Instant> createdAt = _super.createdAt;

    public final NumberPath<Long> id = createNumber("id", Long.class);

    //inherited
    public final DateTimePath<java.time.Instant> updatedAt = _super.updatedAt;

    public final StringPath vc = createString("vc");

    public QCertificateVc(String variable) {
        super(CertificateVc.class, forVariable(variable));
    }

    public QCertificateVc(Path<? extends CertificateVc> path) {
        super(path.getType(), path.getMetadata());
    }

    public QCertificateVc(PathMetadata metadata) {
        super(CertificateVc.class, metadata);
    }

}

