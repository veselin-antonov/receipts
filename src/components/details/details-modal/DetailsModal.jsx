import { useEffect, useState } from 'react';
import PurchasesTable from '../purchases/PurchasesTable';
import Statistics from '../statistics/Statistics';
import BigButton from '../../big-button/BigButton';
import LoadingSpinnner from '../../loading-spinner/LoadingSpinner';

function fetchProductDetails(product, handleDetails, handleLoading) {
  handleLoading(true);
  console.log('Fetching details for product: ', product);
  if (product) {
    fetch(`http://localhost:8080/${product?.id}/details`)
      .then((data) => data.json())
      .then((productDetails) => {
        console.log('Data retrieved:');
        console.log(productDetails);
        handleDetails(productDetails);
        handleLoading(false);
      })
      .catch((error) => {
        console.log(
          'ERROR! Failed to fetch details for product with id: ',
          product.id,
          'Details: ',
          error
        );
        handleLoading(false);
      });
  } else {
    handleLoading(false);
  }
}

function DetailsModal({ product }) {
  const [productDetails, setProductDetails] = useState();
  const [isLoading, setIsLoading] = useState(true);

  console.log('Rendering DetailsModal with state props product: ', product);
  console.log('Rendering DetailsModal with state props isLoading: ', isLoading);

  console.log(
    'Rendering DetailsModal with state variable productDetails: ',
    productDetails
  );

  useEffect(() => {
    fetchProductDetails(product, setProductDetails, setIsLoading);
  }, [product]);

  return (
    <div
      className='modal fade'
      id='details-modal'
      aria-labelledby='detailsModal'
      aria-hidden='true'
    >
      <div className='modal-dialog modal-lg'>
        <div className='modal-content'>
          <div className='modal-header'>
            <h1 className='modal-title'>{product?.name}</h1>
            <button
              type='button'
              className='btn-close'
              data-bs-dismiss='modal'
              aria-label='Close'
            ></button>
          </div>
          <div className='modal-body'>
            {(isLoading && <LoadingSpinnner />) ||
              (productDetails && (
                <>
                  <Statistics stats={productDetails.stats} />
                  <PurchasesTable
                    purchases={productDetails.purchases || []}
                    addNewPurchase={(purchase) => {
                      fetch(`http://localhost:8080/${product.id}/details`, {
                        method: 'POST',
                        headers: {
                          'Content-Type': 'application/json',
                        },
                        body: JSON.stringify(purchase),
                      })
                        .then((response) => response.json())
                        .then((newPurchase) =>
                          setProductDetails({
                            ...productDetails,
                            purchases:
                              [newPurchase, ...productDetails.purchases]
                          })
                        )
                        .catch((error) =>
                          console.log(
                            'Error in POST request when creating new purchase. Details: ',
                            error
                          )
                        );
                    }}
                  />
                </>
              )) || (
                <div className='modal-body'>Няма детайли за този продукт</div>
              )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default DetailsModal;
