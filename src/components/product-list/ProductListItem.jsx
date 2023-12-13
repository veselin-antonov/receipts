function ProductListItem({ product, onClicked }) {
  console.log('Rendering ProductListItem with props: ', product, onClicked);
  return (
    <li
      className='list-item'
      key={product.id}
      data-bs-toggle='modal'
      data-bs-target='#details-modal'
      onClick={() => {
        console.log('Running onItemSelected function with id: ', product.id);
        onClicked(product.id);
      }}
    >
      <div className='icon-container'>
        <img src='/product-placeholder-icon.svg' />
      </div>
      <span className='list-item-text'>{product.name}</span>
      <button className='circle-btn'>
        <svg
          width='21'
          height='21'
          viewBox='0 0 21 21'
          fill='none'
          xmlns='http://www.w3.org/2000/svg'
        >
          <rect x='9' width='3' height='21' rx='1.5' fill='#D9D9D9' />
          <rect y='9' width='21' height='3' rx='1.5' fill='#D9D9D9' />
        </svg>
      </button>
    </li>
  );
}

export default ProductListItem;
